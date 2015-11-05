package org.clueminer.cli;

import au.com.bytecode.opencsv.CSVWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.clueminer.chameleon.Chameleon;
import org.clueminer.clustering.ClusteringExecutorCached;
import org.clueminer.clustering.algorithm.DBSCAN;
import org.clueminer.clustering.algorithm.DBSCANParamEstim;
import org.clueminer.clustering.algorithm.KMeans;
import org.clueminer.clustering.algorithm.cure.CURE;
import org.clueminer.clustering.api.AgglParams;
import org.clueminer.clustering.api.AgglomerativeClustering;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.ClusterEvaluation;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.ClusteringAlgorithm;
import org.clueminer.clustering.api.ClusteringFactory;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.dendrogram.DendrogramMapping;
import org.clueminer.clustering.api.factory.EvaluationFactory;
import org.clueminer.clustering.struct.DendrogramData;
import org.clueminer.dataset.api.Attribute;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dataset.plugin.ArrayDataset;
import org.clueminer.dgram.DgViewer;
import org.clueminer.io.ARFFHandler;
import org.clueminer.io.CsvLoader;
import org.clueminer.io.DataSniffer;
import org.clueminer.io.FileHandler;
import org.clueminer.plot.GnuplotLinePlot;
import org.clueminer.plot.GnuplotScatter;
import org.clueminer.utils.DataFileInfo;
import org.clueminer.utils.DatasetSniffer;
import org.clueminer.utils.Props;
import org.openide.util.Exceptions;

/**
 *
 * @author Tomas Barton
 */
public class Runner implements Runnable {

    private static final Logger logger = Logger.getLogger(Runner.class.getName());
    private final Params params;
    private StopWatch time;

    Runner(Params p) {
        this.params = p;
    }

    protected Dataset<? extends Instance> parseFile(Params p) throws IOException {
        File f = new File(p.data);
        if (!f.exists() || !f.canRead()) {
            throw new InvalidArgumentException("can't read from file " + p.data);
        }

        Dataset<? extends Instance> dataset;
        int clsIndex = p.clsIndex;
        ArrayList<Integer> skip = new ArrayList<>(1);

        DatasetSniffer sniffer = new DataSniffer();
        DataFileInfo df = sniffer.scan(f);
        if (p.type == null) {
            if (df.type == null) {
                p.type = "arff";
            } else {
                p.type = df.type;
            }
        }
        //guess number of attributes
        //TODO: we should be able to estimate number of lines
        dataset = new ArrayDataset(150, df.numAttributes);

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
        Dataset<Instance> dataset = null;
        try {
            dataset = (Dataset<Instance>) parseFile(params);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        if (dataset == null || dataset.isEmpty()) {
            throw new RuntimeException("failed to load any data");
        }

        if (dataset.getName() == null) {
            throw new RuntimeException("missing dataset name");
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
        if (params.experiment == null) {
            params.experiment = safeName(params.algorithm);
        }

        ClusterEvaluation[] evals = loadEvaluation(params.eval);

        Props prop;
        if (params.algParams != null) {
            prop = Props.fromJson(params.algParams);
        } else {
            prop = new Props();
        }

        if (params.hintK) {
            prop.putInt(KMeans.K, dataset.getClasses().size());
        }
        time = new StopWatch(false);
        for (int run = 0; run < params.repeat; run++) {
            if (algorithm instanceof AgglomerativeClustering) {
                prop = hierachical(dataset, prop, algorithm, evals, run);
            } else {
                prop = flatPartitioning(dataset, prop, algorithm, evals, run);
            }
            logger.log(Level.INFO, "finished clustering [run {1}]: {0}", new Object[]{prop.toString(), run});
        }
    }

    private Clustering cluster(Dataset<? extends Instance> dataset, Props prop, ClusteringAlgorithm algorithm) {
        time.startMeasure();
        Clustering clustering = algorithm.cluster(dataset, prop);
        time.endMeasure();
        return clustering;
    }

    private Props hierachical(Dataset<Instance> dataset, Props prop, ClusteringAlgorithm algorithm, ClusterEvaluation[] evals, int run) {
        Clustering clustering;
        //hierarchical result
        ClusteringExecutorCached exec = new ClusteringExecutorCached();
        exec.setAlgorithm(algorithm);
        HierarchicalResult res;

        if (!prop.containsKey(AgglParams.CUTOFF_STRATEGY)) {
            prop.put(AgglParams.CUTOFF_STRATEGY, params.cutoff);
        }
        logger.log(Level.INFO, "clustering rows/columns: {0}", params.cluster);
        time.startMeasure();
        if (params.optimal) {
            res = optHierarchical(exec, dataset, prop, evals);
            prop = res.getClustering().getParams();
        } else {
            res = stdHierarchical(exec, dataset, prop);
        }
        time.endMeasure();
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
                saveHeatmap(params, dataset, res.getDendrogramMapping());
            }
        }
        if (res != null) {
            clustering = res.getClustering();
            if (evals != null) {
                evaluate(clustering, evals, resultsFile(dataset.getName()));
            }
            if (run == 0 && params.scatter) {
                saveScatter(clustering, dataset.getName(), algorithm);
            }
        }
        return prop;
    }

    /**
     * Basic hierarchical clustering
     *
     * @param exec
     * @param dataset
     * @param prop
     * @return
     */
    private HierarchicalResult stdHierarchical(ClusteringExecutorCached exec, Dataset<Instance> dataset, Props prop) {
        HierarchicalResult res = null;
        DendrogramMapping mapping;
        Clustering clustering;
        switch (params.cluster) {
            case "rows":
                clustering = exec.clusterRows(dataset, prop);
                mapping = clustering.getLookup().lookup(DendrogramData.class);
                res = mapping.getRowsResult();
                break;
            case "columns":
                res = exec.hclustColumns(dataset, prop);
                //mapping = new DendrogramData(dataset, null, res);
                break;
            case "both":
                mapping = exec.clusterAll(dataset, prop);
                res = mapping.getRowsResult();
                break;
        }
        return res;
    }

    private HierarchicalResult optHierarchical(ClusteringExecutorCached exec, Dataset<Instance> dataset, Props def, ClusterEvaluation[] evals) {
        HierarchicalResult res;
        HierarchicalResult bestRes = null;
        Clustering clustering;

        if (exec.getAlgorithm() instanceof Chameleon) {
            //test several configurations and return best result
            double maxScore = 0.0, score;
            ClusterEvaluation eval = EvaluationFactory.getInstance().getProvider(params.optEval);
            //auto pilot

            String[] configs = new String[]{
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska}", //auto
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,closeness_priority:2.0,interconnectivity_priority:4.0}",
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,closeness_priority:3.0,interconnectivity_priority:1.0}",
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,closeness_priority:1.0,interconnectivity_priority:2.0}",
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,closeness_priority:2.0,interconnectivity_priority:4.0,k:15}",
                "{cutoff-strategy:External_cutoff,similarity_measure:Standard}", //std
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,noise_detection:1}", //noise detection
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,closeness_priority:2.0,interconnectivity_priority:4.0,noise_detection:1}",
                "{cutoff-strategy:External_cutoff,similarity_measure:Shatovska,closeness_priority:1.0,interconnectivity_priority:2.0,noise_detection:1}"
            };
            Props prop;
            for (String config : configs) {
                prop = def.copy();
                prop.merge(Props.fromJson(config));
                System.out.println("using prop: " + prop.toString());
                res = stdHierarchical(exec, dataset, prop);
                clustering = res.getClustering();
                score = eval.score(clustering, prop);
                if (eval.isBetter(score, maxScore)) {
                    maxScore = score;
                    bestRes = res;
                }
                evaluate(clustering, evals, resultsFile(dataset.getName()));
            }
            return bestRes;

        } else {
            return stdHierarchical(exec, dataset, def);
        }
    }

    private Props flatPartitioning(Dataset<Instance> dataset, Props prop, ClusteringAlgorithm algorithm, ClusterEvaluation[] evals, int run) {
        Clustering clustering = null;
        //try to find optimal clustering
        if (params.optimal) {
            optFlatPartitioning(dataset, prop, algorithm, evals, run);
        } else {
            clustering = cluster(dataset, prop, algorithm);
        }

        if (clustering != null) {
            evaluate(clustering, evals, resultsFile(dataset.getName()));
            if (run == 0 && params.scatter) {
                saveScatter(clustering, dataset.getName(), algorithm);
            }
            return clustering.getParams();
        } else {
            logger.log(Level.WARNING, "failed to find solution");
        }
        return prop;
    }

    private Clustering optFlatPartitioning(Dataset<Instance> dataset, Props prop, ClusteringAlgorithm algorithm, ClusterEvaluation[] evals, int run) {
        Clustering clustering = null;
        Clustering curr;
        int cnt = 0;
        if (algorithm instanceof DBSCAN) {
            double bestEps = 0;
            int bestPts = 0;
            int maxSize = (int) Math.sqrt(dataset.size());
            double maxScore = 0.0, score;
            ClusterEvaluation eval = EvaluationFactory.getInstance().getProvider(params.optEval);

            DBSCANParamEstim<Instance> dbscanParam = new DBSCANParamEstim();
            dbscanParam.estimate((Dataset<Instance>) dataset, prop);

            //plot k-dist
            GnuplotLinePlot<Instance, Cluster<Instance>> chart = new GnuplotLinePlot<>(workDir() + File.separatorChar + dataset.getName());
            chart.plot(dbscanParam, dataset, "4-dist plot " + dataset.getName());

            double epsMax = dbscanParam.getMaxEps();
            double epsMin = dbscanParam.getMinEps();
            double step = (epsMax - epsMin) / 10.0;

            System.out.println("min = " + epsMin + ", max = " + epsMax);
            //we have to guess parameters
            double eps;
            for (int i = 4; i <= 10; i++) {
                prop.putInt(DBSCAN.MIN_PTS, i);
                eps = epsMax;
                while (eps > epsMin) {
                    prop.putDouble(DBSCAN.EPS, eps);
                    curr = cluster(dataset, prop, algorithm);
                    score = eval.score(curr, prop);
                    System.out.println("eps = " + eps + " minPts = " + i + " => " + eval.getName() + ": " + score + ", clusters: " + curr.size());
                    if (eval.isBetter(score, maxScore)) {
                        maxScore = score;
                        clustering = curr;
                        bestEps = eps;
                        bestPts = i;
                    }
                    cnt++;
                    eps -= step; //eps increment
                    if (curr.size() == 1 || curr.size() >= maxSize) {
                        break;
                    }
                }
            }
            prop.putDouble(DBSCAN.EPS, bestEps);
            prop.putInt(DBSCAN.MIN_PTS, bestPts);
        } else if (algorithm instanceof CURE) {
            //we don't have any heuristic for CURE yet
            double maxScore = 0.0, score;
            ClusterEvaluation eval = EvaluationFactory.getInstance().getProvider("NMI-sqrt");
            double shrink = 0.1;
            double bestShrink = 0;
            while (shrink < 1.0) {
                prop.putDouble(CURE.SHRINK_FACTOR, shrink);
                curr = cluster(dataset, prop, algorithm);
                score = eval.score(curr, prop);
                System.out.println("shrink = " + shrink + " => " + eval.getName() + ": " + score + ", clusters: " + curr.size());
                if (eval.isBetter(score, maxScore)) {
                    maxScore = score;
                    clustering = curr;
                    bestShrink = shrink;
                }
                cnt++;
                shrink += 0.1; //eps increment
            }
            prop.put(CURE.SHRINK_FACTOR, bestShrink);
        } else {
            clustering = cluster(dataset, prop, algorithm);
        }
        logger.log(Level.INFO, "{0}: evaluated {1} clusterings", new Object[]{algorithm.getName(), cnt});
        return clustering;
    }

    private File resultsFile(String fileName) {
        String path = workDir() + File.separatorChar + fileName + ".csv";
        return new File(path);
    }

    /**
     * Evaluate
     *
     * @param clustering
     * @param evals
     * @param results
     */
    private void evaluate(Clustering clustering, ClusterEvaluation[] evals, File results) {
        if (evals == null) {
            return;
        }
        Dataset<? extends Instance> dataset = clustering.getLookup().lookup(Dataset.class);
        if (dataset == null) {
            throw new RuntimeException("dataset not in clustering lookup!");
        }
        String[] line;
        int extraAttr = 4;
        double score;

        //header
        if (!results.exists()) {
            line = new String[evals.length + extraAttr];
            int i = 0;
            line[i++] = "dataset";
            line[i++] = "clusters";
            for (ClusterEvaluation e : evals) {
                line[i++] = e.getName();
            }
            line[i++] = "Time (ms)";
            line[i++] = "Params";
            logger.log(Level.INFO, "writing results into: {0}", results.getAbsolutePath());
            writeCsvLine(results, line, false);
        }

        line = new String[evals.length + extraAttr];
        int i = 0;
        line[i++] = dataset.getName();
        line[i++] = String.valueOf(clustering.size());
        for (ClusterEvaluation e : evals) {
            score = e.score(clustering);
            line[i++] = String.valueOf(score);
            System.out.println(e.getName() + ": " + score);
        }
        line[i++] = time.formatMs();
        line[i++] = clustering.getParams().toJson();
        writeCsvLine(results, line, true);
    }

    public void writeCsvLine(File file, String[] columns, boolean apend) {
        try (PrintWriter writer = new PrintWriter(
                new FileOutputStream(file, apend)
        )) {

            CSVWriter csv = new CSVWriter(writer, params.separator.charAt(0));
            csv.writeNext(columns, false);
            writer.close();

        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
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
        DgViewer panel = new DgViewer();
        panel.setDataset(mapping);
        //pixels per element in matrix
        double mult = 20.0;
        int width = (int) (params.width + dataset.attributeCount() * 40);
        int height = (int) (params.height + dataset.size() * mult);
        logger.log(Level.INFO, "resolution {0} x {1}", new Object[]{width, height});
        BufferedImage image = panel.getBufferedImage(width, height);
        String path = FileUtil.mkdir(workDir() + File.separatorChar + dataset.getName());
        File file = new File(path + File.separatorChar + safeName(params.algorithm) + ".png");
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

    public String workDir() {
        String path = params.home + File.separatorChar + params.experiment;
        return FileUtil.mkdir(path);
    }

    private void saveScatter(Clustering clustering, String subdir, ClusteringAlgorithm algorithm) {
        if (params.scatter) {
            String dir = FileUtil.mkdir(workDir() + File.separatorChar + subdir);
            logger.log(Level.INFO, "writing scatter to {0}", dir);
            GnuplotScatter<Instance, Cluster<Instance>> scatter = new GnuplotScatter<>(dir);
            String title = algorithm.getName() + " - " + clustering.getParams().toString();
            scatter.plot(clustering, title);
        }
    }

}
