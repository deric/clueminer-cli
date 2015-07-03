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
import org.clueminer.clustering.api.AgglParams;
import org.clueminer.clustering.api.AgglomerativeClustering;
import org.clueminer.clustering.api.ClusteringAlgorithm;
import org.clueminer.clustering.api.ClusteringFactory;
import org.clueminer.clustering.api.Executor;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.dendrogram.DendrogramMapping;
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
                if (clsIndex > 0) {
                    FileHandler.loadDataset(f, dataset, p.separator);
                } else {
                    FileHandler.loadDataset(f, dataset, clsIndex, p.separator);
                }
                break;
            case "arff":
                ARFFHandler arff = new ARFFHandler();
                if (clsIndex > 0) {
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

        logger.log(Level.INFO, "loaded dataset with {0} instances, {1} attributes",
                new Object[]{dataset.size(), dataset.attributeCount()});
        ClusteringAlgorithm algorithm = parseAlgorithm(params);
        for (Entry<Integer, Attribute> e : dataset.getAttributes().entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }

        if (algorithm == null) {
            throw new RuntimeException("failed to load algorithm '" + params.algorithm + "'");
        }

        if (algorithm instanceof AgglomerativeClustering) {
            Executor exec = new ClusteringExecutorCached();
            exec.setAlgorithm((AgglomerativeClustering) algorithm);
            HierarchicalResult res = null;
            DendrogramMapping mapping = null;
            Props prop = new Props();
            prop.put(AgglParams.CUTOFF_STRATEGY, params.cutoff);
            switch (params.cluster) {
                case "rows":
                    res = exec.hclustRows(dataset, prop);
                    break;
                case "columns":
                    res = exec.hclustRows(dataset, prop);
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
            throw new RuntimeException("non-hierarchical algorithms are not supported yet");
        }
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

        File file = new File(params.home + File.separatorChar + name + ".png");
        logger.log(Level.INFO, "saving heatmap to {0}", file.getAbsolutePath());
        String format = "png";
        try {
            ImageIO.write(image, format, file);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

}
