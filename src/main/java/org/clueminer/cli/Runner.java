package org.clueminer.cli;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.clueminer.clustering.ClusteringExecutorCached;
import org.clueminer.clustering.api.AgglomerativeClustering;
import org.clueminer.clustering.api.ClusteringAlgorithm;
import org.clueminer.clustering.api.ClusteringFactory;
import org.clueminer.clustering.api.Executor;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.dendrogram.DendrogramMapping;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dataset.plugin.ArrayDataset;
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
        switch (p.type) {
            case "csv":
                CsvLoader csvLoad = new CsvLoader();
                csvLoad.setSeparator(p.separator.charAt(0));
                if (clsIndex > 0) {
                    csvLoad.setClassIndex(clsIndex);
                }
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

        if (algorithm == null) {
            throw new RuntimeException("failed to load algorithm '" + params.algorithm + "'");
        }

        if (algorithm instanceof AgglomerativeClustering) {
            Executor exec = new ClusteringExecutorCached();
            exec.setAlgorithm((AgglomerativeClustering) algorithm);
            HierarchicalResult res = null;
            Props prop = new Props();
            switch (params.cluster) {
                case "rows":
                    res = exec.hclustRows(dataset, prop);
                    break;
                case "columns":
                    res = exec.hclustRows(dataset, prop);
                    break;
                case "both":
                    DendrogramMapping mapping = exec.clusterAll(dataset, prop);
                    res = mapping.getRowsResult();
                    break;
            }
            if (res != null) {
                res.getTreeData().print();
            }
        } else {
            throw new RuntimeException("non-hierarchical algorithms are not supported yet");
        }
    }

}
