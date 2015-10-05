package org.clueminer.cli;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.clueminer.clustering.ClusteringExecutorCached;
import org.clueminer.clustering.algorithm.KMeans;
import org.clueminer.clustering.api.AgglParams;
import org.clueminer.clustering.api.AgglomerativeClustering;
import org.clueminer.clustering.api.ClusterEvaluation;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.ClusteringAlgorithm;
import org.clueminer.clustering.api.ClusteringFactory;
import org.clueminer.clustering.api.Executor;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.dendrogram.DendrogramMapping;
import org.clueminer.clustering.api.factory.EvaluationFactory;
import org.clueminer.clustering.struct.DendrogramData2;
import org.clueminer.dataset.api.Attribute;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dataset.plugin.ArrayDataset;
import org.clueminer.dgram.DgViewer;
import org.clueminer.io.ARFFHandler;
import org.clueminer.io.CsvLoader;
import org.clueminer.io.FileHandler;
import org.clueminer.utils.Props;
import org.openide.util.Exceptions;

/**
 *
 * @author Tomas Barton
 */
public class Runner implements Runnable {

    private final Params params;
    private static final Logger logger = Logger.getLogger(Runner.class.getName());

    Runner(Params p) {
        this.params = p;
    }

    protected Dataset<? extends Instance> parseFile(Params p) throws IOException {
        File f = new File(p.data);
        if (!f.exists() || !f.canRead()) {
            throw new InvalidArgumentException("can't read from file " + p.data);
        }

        Dataset<? extends Instance> dataset = new ArrayDataset(150, 5);
        int clsIndex = p.clsIndex;
        ArrayList<Integer> skip = new ArrayList<>(1);
        switch (p.type) {
            case "csv":
                CsvLoader csvLoad = new CsvLoader();
                csvLoad.setSeparator(p.separator.charAt(0));
                if (clsIndex > -1) {
                    csvLoad.setClassIndex(clsIndex);
                }
                if (p.skip != null) {
                    String[] idx = p.skip.split(",");
                    for (String id : idx) {
                        skip.add(Integer.valueOf(id));
                    }
                }
                if (p.idIndex > -1) {
                    csvLoad.addNameAttr(p.idIndex);
                    skip.add(p.idIndex);
                }
                csvLoad.setSkipIndex(skip);
                csvLoad.setHasHeader(p.header);
                csvLoad.load(f, dataset);
                break;
            case "txt":
                if (clsIndex > -1) {
                    FileHandler.loadDataset(f, dataset, p.separator);
                } else {
                    FileHandler.loadDataset(f, dataset, clsIndex, p.separator);
                }
                break;
            case "arff":
                ARFFHandler arff = new ARFFHandler();
                if (clsIndex > -1) {
                    arff.load(f, dataset, clsIndex);
                } else {
                    arff.load(f, dataset);
                }
                break;
            default:
                throw new InvalidArgumentException("file format " + p.type + " is not supported");
        }

        return dataset;
    }

    protected ClusteringAlgorithm parseAlgorithm(Params p) {
        ClusteringAlgorithm algorithm = ClusteringFactory.getInstance().getProvider(p.algorithm);
        return algorithm;
    }

    @Override
    public void run() {
        Dataset<? extends Instance> dataset = null;
        try {
            dataset = parseFile(params);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        if (dataset == null || dataset.isEmpty()) {
            throw new RuntimeException("failed to load any data");
        }

        logger.log(Level.INFO, "loaded dataset \"{2}\" with {0} instances, {1} attributes",
                new Object[]{dataset.size(), dataset.attributeCount(), dataset.getName()});
        ClusteringAlgorithm algorithm = parseAlgorithm(params);
        for (Entry<Integer, Attribute> e : dataset.getAttributes().entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }

        if (algorithm == null) {
            throw new RuntimeException("failed to load algorithm '" + params.algorithm + "'");
        }

        ClusterEvaluation[] evals = loadEvaluation(params.eval);

        Props prop = new Props();
        if (params.hintK) {
            prop.putInt(KMeans.K, dataset.getClasses().size());
        }
        if (algorithm instanceof AgglomerativeClustering) {
            //hierarchical result
            Executor exec = new ClusteringExecutorCached();
            exec.setAlgorithm(algorithm);
            HierarchicalResult res = null;
            DendrogramMapping mapping = null;

            prop.put(AgglParams.CUTOFF_STRATEGY, params.cutoff);
            logger.log(Level.INFO, "clustering rows/columns: {0}", params.cluster);
            switch (params.cluster) {
                case "rows":
                    res = exec.hclustRows(dataset, prop);
                    mapping = new DendrogramData2(dataset, res);
                    break;
                case "columns":
                    res = exec.hclustColumns(dataset, prop);
                    mapping = new DendrogramData2(dataset, null, res);
                    break;
                case "both":
                    mapping = exec.clusterAll(dataset, prop);
                    res = mapping.getRowsResult();
                    break;
            }
            if (res != null) {
                if (params.matrix) {
                    if (res.getProximityMatrix() != null) {
                        res.getProximityMatrix().printLower(5, 2);
                    }
                }
                if (params.tree) {
                    res.getTreeData().print();
                }

                if (params.heatmap) {
                    saveHeatmap(params, dataset, mapping);
                }
            }
        } else {
            //flat partitioning
            Clustering clustering = algorithm.cluster(dataset, prop);
            evaluate(clustering, evals);
        }

        logger.log(Level.INFO, "finished clustering: {0}", prop.toString());
    }

    private void evaluate(Clustering clustering, ClusterEvaluation[] evals) {
        if (evals != null) {
            double score;
            for (ClusterEvaluation e : evals) {
                score = e.score(clustering);
                System.out.println(e.getName() + ": " + score);
            }
        }
    }

    private ClusterEvaluation[] loadEvaluation(String metrics) {
        ClusterEvaluation[] evals = null;
        if (!metrics.isEmpty()) {
            String[] names = params.eval.split(",");
            EvaluationFactory ef = EvaluationFactory.getInstance();
            if (names.length < 1) {
                throw new RuntimeException("please provide comma separated names of metrics");
            }
            evals = new ClusterEvaluation[names.length];
            int i = 0;
            for (String m : names) {
                evals[i++] = ef.getProvider(m);
            }
        }
        return evals;
    }

    private void saveHeatmap(Params params, Dataset<? extends Instance> dataset, DendrogramMapping mapping) {
        String name = dataset.getName();
        if (name == null) {
            RandomString rand = new RandomString(8);
            name = rand.nextString();
        }
        DgViewer panel = new DgViewer();
        panel.setDataset(mapping);
        //pixels per element in matrix
        double mult = 20.0;
        int width = (int) (params.width + dataset.attributeCount() * 40);
        int height = (int) (params.height + dataset.size() * mult);
        logger.log(Level.INFO, "resolution {0} x {1}", new Object[]{width, height});
        BufferedImage image = panel.getBufferedImage(width, height);

        File file = new File(params.home + File.separatorChar + name + "-" + safeName(params.algorithm) + ".png");
        logger.log(Level.INFO, "saving heatmap to {0}", file.getAbsolutePath());
        String format = "png";
        try {
            ImageIO.write(image, format, file);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public static String safeName(String name) {
        return name.toLowerCase().replace(" ", "_");
    }

}
