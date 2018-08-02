/*
 * Copyright (C) 2011-2016 clueminer.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.clueminer.cli;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import org.clueminer.ap.AffinityPropagation;
import org.clueminer.chameleon.Chameleon;
import org.clueminer.clustering.ClusteringExecutorCached;
import org.clueminer.clustering.algorithm.DBSCAN;
import org.clueminer.clustering.algorithm.DBSCANParamEstim;
import org.clueminer.clustering.algorithm.KMeans;
import org.clueminer.clustering.algorithm.cure.CURE;
import org.clueminer.clustering.api.AgglomerativeClustering;
import org.clueminer.clustering.api.AlgParams;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.ClusterEvaluation;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.ClusteringAlgorithm;
import org.clueminer.clustering.api.ClusteringFactory;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.ScoreException;
import org.clueminer.clustering.api.dendrogram.DendrogramMapping;
import org.clueminer.clustering.api.factory.EvaluationFactory;
import org.clueminer.clustering.api.factory.ExternalEvaluatorFactory;
import org.clueminer.clustering.api.factory.InternalEvaluatorFactory;
import org.clueminer.clustering.struct.DendrogramData;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dataset.impl.ArrayDataset;
import org.clueminer.dgram.DgViewer;
import org.clueminer.evolution.api.Individual;
import org.clueminer.exception.ParserError;
import org.clueminer.io.DataSniffer;
import org.clueminer.io.FileHandler;
import org.clueminer.io.arff.ARFFHandler;
import org.clueminer.io.csv.CsvLoader;
import org.clueminer.meta.engine.MetaSearch;
import org.clueminer.meta.ranking.ParetoFrontQueue;
import org.clueminer.plot.GnuplotLinePlot;
import org.clueminer.plot.GnuplotScatter;
import org.clueminer.utils.DataFileInfo;
import org.clueminer.utils.DatasetSniffer;
import org.clueminer.utils.Props;
import org.clueminer.utils.StopWatch;
import org.openide.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Tomas Barton
 * @param <I> individual represents mutable solution
 * @param <E>
 * @param <C>
 */
public class Runner<I extends Individual<I, E, C>, E extends Instance, C extends Cluster<E>> implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(Runner.class);
    private final CliParams cliParams;
    private StopWatch time;
    private final ResultsExporter export;
    private String sha1;

    Runner(CliParams p) {
        this.cliParams = p;
        this.export = new ResultsExporter(this);
    }

    protected Dataset<E> loadData(CliParams p) throws IOException, ParserError, FileNotFoundException, NoSuchAlgorithmException {
        Dataset<E> dataset;
        if (p.generate != null) {
            DataGenerator<E> gen = new DataGenerator<>();
            int n = 100;
            int d = 5;
            Pattern pat = Pattern.compile("(\\d+)x(\\d+)");
            Matcher m = pat.matcher(p.generate);
            LOG.info("generate: {}", p.generate);
            if (m.matches()) {
                if (m.group(1) != null && !m.group(1).isEmpty()) {
                    n = Integer.parseInt(m.group(1));
                }
                if (m.group(2) != null && !m.group(2).isEmpty()) {
                    d = Integer.parseInt(m.group(2));
                }
            }

            dataset = gen.generateData(n, d);
            return dataset;
        }
        File f = new File(p.data);
        if (!f.exists() || !f.canRead()) {
            throw new InvalidArgumentException("can't read from file " + p.data);
        }
        sha1 = computeSha1(f);
        LOG.info("file: {}, SHA-1: {}", p.data, sha1);

        int clsIndex = p.clsIndex;
        ArrayList<Integer> skip = new ArrayList<>(1);

        DatasetSniffer sniffer = new DataSniffer();
        DataFileInfo df = sniffer.scan(f);
        if (p.type == null) {
            if (df.type == null) {
                p.type = "arff";
            } else {
                p.type = df.type;
                LOG.info("auto-detected type {}", p.type);
            }
        }
        //guess number of attributes
        //TODO: we should be able to estimate number of lines
        dataset = new ArrayDataset(150, df.numAttributes);

        LOG.info("parsing dataset as {}", p.type);
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

    protected ClusteringAlgorithm parseAlgorithm(String alg) {
        ClusteringAlgorithm algorithm = ClusteringFactory.getInstance().getProvider(alg);
        return algorithm;
    }

    @Override
    public void run() {
        Dataset<E> dataset = null;
        try {
            dataset = (Dataset<E>) loadData(cliParams);
        } catch (IOException | ParserError | NoSuchAlgorithmException ex) {
            Exceptions.printStackTrace(ex);
        }
        if (dataset == null || dataset.isEmpty()) {
            throw new RuntimeException("failed to load any data");
        }

        if (dataset.getName() == null) {
            throw new RuntimeException("missing dataset name");
        }

        ClusterEvaluation[] evals = loadEvaluation(cliParams.eval);

        LOG.info("loaded dataset \"{}\" with {} instances, {} attributes", dataset.getName(), dataset.size(), dataset.attributeCount());
        if (cliParams.metaSearch) {
            if (cliParams.experiment == null) {
                cliParams.experiment = "meta-search" + File.separatorChar + safeName(dataset.getName());
            } else {
                cliParams.experiment = cliParams.experiment + File.separatorChar + safeName(dataset.getName());
            }
            ExecutorService pool = Executors.newFixedThreadPool(cliParams.repeat);
            Future<ParetoFrontQueue> future;
            MetaSearch<I, E, C> metaSearch;
            StopWatch evalTime;

            metaSearch = new MetaSearch<>();
            Props metaParams = parseJson(cliParams.getMetaParams());
            LOG.debug("meta-params: {}", metaParams.toString());
            metaSearch.configure(metaParams);
            metaSearch.setDataset(dataset);
            Callable<ParetoFrontQueue> callable = metaSearch;

            for (int run = 0; run < cliParams.repeat; run++) {
                LOG.info("RUN {}", run);
                future = pool.submit(callable);
                try {
                    if (future != null) {
                        evalTime = new StopWatch(true);
                        ParetoFrontQueue<E, C, Clustering<E, C>> q = future.get();
                        SortedMap<Double, Clustering<E, C>> ranking = q.computeRanking();
                        if (evals != null) {
                            export.exportFront(ranking, evals, export.resultsFile("pareto-front-" + run));
                        }

                        //internal evaluation
                        InternalEvaluatorFactory ief = InternalEvaluatorFactory.getInstance();
                        File res = export.createNewFile(dataset, "internal-" + run);
                        evals = ief.getAllArray();
                        export.ranking(ranking, evals, res);

                        LOG.info("Computing correlation to {}", cliParams.optEval);
                        //ranking correlation
                        ClusterEvaluation supervised = EvaluationFactory.getInstance().getProvider(cliParams.optEval);
                        res = export.resultsFile(dataset.getName() + "-correlation");
                        export.correlation(ranking, evals, res, supervised, q, metaParams);

                        //supervised coefficients
                        LOG.info("Computing unsupervised cooeficients");
                        ExternalEvaluatorFactory eef = ExternalEvaluatorFactory.getInstance();
                        res = export.createNewFile(dataset, "external-" + run);
                        evals = eef.getAllArray();
                        export.ranking(ranking, evals, res);
                        Clustering c = q.poll();
                        LOG.info("best template: {}", c.getParams().toJson());
                        LOG.info("best fingerprint: {}", c.fingerprint());
                        evalTime.endMeasure();
                        LOG.info("computing evaluations took: {}s", evalTime.formatSec());
                    } else {
                        throw new RuntimeException("there's no future in here");
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            export.writeMeta(metaSearch.getMeta(), dataset);

            pool.shutdown();
            //System.exit(0);
            return;
        }

        Props prop = parseJson(cliParams.getParams());
        LOG.info("params: {}", prop.toString());

        String alg = prop.get("algorithm", cliParams.algorithm);
        ClusteringAlgorithm algorithm = parseAlgorithm(alg);
        if (algorithm == null) {
            throw new RuntimeException("failed to load algorithm '" + alg + "'");
        }
        if (cliParams.experiment == null) {
            cliParams.experiment = safeName(alg);
        }

        if (cliParams.hintK) {
            prop.putInt(KMeans.K, dataset.getClasses().size());
        }
        time = new StopWatch(false);
        for (int run = 0; run < cliParams.repeat; run++) {
            LOG.debug("executing {}", prop.toJson());
            if (algorithm instanceof AgglomerativeClustering) {
                prop = hierachical(dataset, prop, algorithm, evals, run);
            } else {
                prop = flatPartitioning(dataset, prop, algorithm, evals, run);
            }
            LOG.info("finished clustering [run {}]: {}", new Object[]{prop.toString(), run});
            LOG.info("total time {}ms, in seconds: {}", new Object[]{time.formatMs(), time.formatSec()});

        }
    }

    /**
     * Parse either JSON string or load JSON from a file (path to a file)
     *
     * @param json
     * @return Props object (a hash map)
     */
    private Props parseJson(String json) {
        Props prop = null;
        if (!json.isEmpty()) {
            if (!json.startsWith("{")) {
                File f = new File(json.trim());
                if (f.exists()) {
                    try {
                        json = Files.toString(f, Charsets.UTF_8);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            try {
                prop = Props.fromJson(json);
            } catch (JsonSyntaxException | IllegalStateException e) {
                System.err.println("ERROR: Failed to parse algorithm parameters: '" + json + "'. Make sure you'll pass a valid JSON.");
                Exceptions.printStackTrace(e);
                System.exit(2);
            }
        } else {
            prop = new Props();
        }
        return prop;
    }

    private Clustering cluster(Dataset<? extends Instance> dataset, Props prop, ClusteringAlgorithm algorithm) {
        time.startMeasure();
        Clustering clustering = algorithm.cluster(dataset, prop);
        time.endMeasure();
        clustering.lookupAdd(time);
        return clustering;
    }

    private Props hierachical(Dataset<E> dataset, Props prop, ClusteringAlgorithm algorithm, ClusterEvaluation[] evals, int run) {
        Clustering clustering;
        //hierarchical result
        ClusteringExecutorCached exec = new ClusteringExecutorCached();
        exec.setAlgorithm(algorithm);
        HierarchicalResult res;

        if (!prop.containsKey(AlgParams.CUTOFF_STRATEGY)) {
            prop.put(AlgParams.CUTOFF_STRATEGY, cliParams.cutoff);
        }
        LOG.info("clustering rows/columns: {}", cliParams.cluster);
        time.startMeasure();
        if (cliParams.optimal) {
            res = optHierarchical(exec, dataset, prop, evals);
            prop = res.getClustering().getParams();
        } else {
            res = stdHierarchical(exec, dataset, prop);
        }
        if (res != null) {
            if (cliParams.matrix) {
                if (res.getProximityMatrix() != null) {
                    res.getProximityMatrix().printLower(5, 2);
                }
            }
            if (cliParams.tree) {
                res.getTreeData().print();
            }

            if (cliParams.heatmap) {
                saveHeatmap(cliParams, dataset, res.getDendrogramMapping());
            }
        }
        if (res != null) {
            clustering = res.getClustering();
            clustering.lookupAdd(time);
            if (evals != null) {
                export.evaluate(clustering, evals, dataset);
            }
            if (run == 0 && cliParams.scatter) {
                saveScatter(clustering, dataset.getName(), algorithm);
            }
            LOG.info("got {} clusters", clustering.size());
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
    private HierarchicalResult stdHierarchical(ClusteringExecutorCached exec, Dataset<E> dataset, Props prop) {
        HierarchicalResult res = null;
        DendrogramMapping mapping;
        Clustering clustering;
        switch (cliParams.cluster) {
            case "rows":
                HierarchicalResult rowsResult = exec.hclustRows(dataset, prop);
                time.endMeasure();
                //don't count time required for finding best cutoff into total
                exec.findCutoff(rowsResult, prop);
                mapping = new DendrogramData(dataset, rowsResult);

                clustering = rowsResult.getClustering();
                clustering.mergeParams(prop);
                clustering.lookupAdd(mapping);

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

    private HierarchicalResult optHierarchical(ClusteringExecutorCached exec, Dataset<E> dataset, Props def, ClusterEvaluation[] evals) {
        if (exec.getAlgorithm() instanceof Chameleon) {
            //test several configurations and return best result
            String[] configs;
            String ch1;
            switch (cliParams.method) {
                case "Ch1":
                    //overwrite only necessary parameters
                    ch1 = "partitioning:hMETIS,bisection:hMETIS,internal_noise_threshold:1,noise_detection:0";
                    configs = new String[]{
                        "{" + ch1 + ",ctype=h12}",
                        "{" + ch1 + ",ctype=fc1}",
                        "{" + ch1 + ",ctype=fc2}",
                        "{" + ch1 + ",ctype=gfc1}",
                        "{" + ch1 + ",ctype=gfc2}",
                        "{" + ch1 + ",ctype=h1}",
                        "{" + ch1 + ",ctype=h2}",
                        "{" + ch1 + ",ctype=edge1}",
                        "{" + ch1 + ",ctype=edge2}",
                        "{" + ch1 + ",ctype=gedge1}",
                        "{" + ch1 + ",ctype=gedge2}"
                    };
                    return findBestHclust(configs, exec, dataset, def, evals);
                case "Ch2-hmetis":
                    ch1 = "partitioning:hMETIS,bisection:hMETIS,internal_noise_threshold:1,noise_detection:0";
                    String[] ch2c = new String[]{
                        "k-estim:log10",
                        "closeness_priority:2.0,interconnectivity_priority:3.0",
                        "closeness_priority:3.0,interconnectivity_priority:1.0",
                        "closeness_priority:1.0,interconnectivity_priority:2.0",
                        "closeness_priority:2.0,interconnectivity_priority:4.0,k:14",
                        "closeness_priority:2.0,interconnectivity_priority:1.0,k:25",
                        "closeness_priority:2.0,interconnectivity_priority:1.0,k:3"
                    };
                    String[] ch1c = new String[]{
                        ",ctype=h12",
                        ",ctype=fc1",
                        ",ctype=fc2",
                        ",ctype=gfc1",
                        ",ctype=gfc2",
                        ",ctype=h1",
                        ",ctype=h2",
                        ",ctype=edge1",
                        ",ctype=edge2",
                        ",ctype=gedge1",
                        ",ctype=gedge2"
                    };
                    //cartesian product
                    configs = new String[ch1c.length * ch2c.length];
                    int i = 0;
                    for (String c1 : ch1c) {
                        for (String c2 : ch2c) {
                            configs[i++] = "{" + ch1 + c1 + "," + c2 + "}";
                        }
                    }
                    return findBestHclust(configs, exec, dataset, def, evals);
                case "Ch2nn":
                    //ch2 - without noise detection
                    configs = new String[]{
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1}", //auto
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1,k-estim:log10}", //higher k
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1,closeness_priority:2.0,interconnectivity_priority:4.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1,closeness_priority:2.0,interconnectivity_priority:3.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1,closeness_priority:3.0,interconnectivity_priority:1.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1,closeness_priority:1.0,interconnectivity_priority:2.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:BBK1,closeness_priority:2.0,interconnectivity_priority:4.0,k:14}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:Standard}", //std
                    };
                    return findBestHclust(configs, exec, dataset, def, evals);
                case "Ch2s1":
                case "Ch2s2":
                case "Ch2s3":
                case "Ch2s4":
                case "Ch2s5":
                    String sim;
                    switch (cliParams.method) {
                        case "Ch2s2":
                            sim = "BBK2";
                            break;
                        case "Ch2s3":
                            sim = "BBK3";
                            break;
                        case "Ch2s4":
                            sim = "BBK4";
                            break;
                        case "Ch2s5":
                            sim = "BBK5";
                            break;
                        default:
                            sim = "BBK1";
                    }
                    //ch2
                    configs = new String[]{
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + "}", //auto
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",k-estim:log10}", //higher k
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:2.0,interconnectivity_priority:4.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:3.0,interconnectivity_priority:1.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:1.0,interconnectivity_priority:2.0}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:2.0,interconnectivity_priority:4.0,k:14}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",noise_detection:1}", //noise detection
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:2.0,interconnectivity_priority:4.0,noise_detection:1}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:1.0,interconnectivity_priority:2.0,noise_detection:1}",
                        "{cutoff-strategy:External_cutoff,similarity_measure:" + sim + ",closeness_priority:2.0,interconnectivity_priority:3.0,noise_detection:1,k:10}"
                    };

                    return findBestHclust(configs, exec, dataset, def, evals);
                default:
                    //ch2 - configuration is merged with user supplied config
                    configs = new String[]{
                        "{k-estim:log10}",
                        "{k-estim:10ln}",
                        "{k-estim:8ln}",
                        "{k-estim:4ln}",
                        "{k-estim:cln}",
                        "{closeness_priority:2.0,interconnectivity_priority:3.0}",
                        "{closeness_priority:3.0,interconnectivity_priority:1.0}",
                        "{closeness_priority:1.0,interconnectivity_priority:2.0}",
                        "{closeness_priority:2.0,interconnectivity_priority:4.0,k:14}",
                        "{closeness_priority:2.0,interconnectivity_priority:1.0,k:25}",
                        "{closeness_priority:2.0,interconnectivity_priority:1.0,k:3}"
                    };

                    return findBestHclust(configs, exec, dataset, def, evals);
            }
        } else {
            return stdHierarchical(exec, dataset, def);
        }
    }

    private HierarchicalResult findBestHclust(String[] configs, ClusteringExecutorCached exec, Dataset<E> dataset, Props def, ClusterEvaluation[] evals) {
        double maxScore = 0.0, score;
        Props prop;
        ClusterEvaluation eval = EvaluationFactory.getInstance().getProvider(cliParams.optEval);
        Clustering clustering;
        HierarchicalResult res;
        HierarchicalResult bestRes = null;
        for (String config : configs) {
            prop = def.copy();
            prop.merge(Props.fromJson(config));
            System.out.println("using prop: " + prop.toString());
            time = new StopWatch(true);
            res = stdHierarchical(exec, dataset, prop);
            clustering = res.getClustering();
            clustering.lookupAdd(time);
            try {
                score = eval.score(clustering, prop);
            } catch (ScoreException ex) {
                score = Double.NaN;
                LOG.warn("failed to compute score {}: {}", new Object[]{eval.getName(), ex.getMessage()});
            }
            if (eval.isBetter(score, maxScore)) {
                maxScore = score;
                bestRes = res;
            }
            export.evaluate(clustering, evals, dataset);
        }
        return bestRes;
    }

    private Props flatPartitioning(Dataset<E> dataset, Props prop, ClusteringAlgorithm algorithm, ClusterEvaluation[] evals, int run) {
        Clustering clustering;
        //try to find optimal clustering
        if (cliParams.optimal) {
            clustering = optFlatPartitioning(dataset, prop, algorithm, evals, run);
        } else {
            clustering = cluster(dataset, prop, algorithm);
        }

        if (clustering != null) {
            LOG.info("got {} clusters", clustering.size());
            export.evaluate(clustering, evals, dataset);
            if (run == 0 && cliParams.scatter) {
                saveScatter(clustering, dataset.getName(), algorithm);
            }
            return clustering.getParams();
        } else {
            LOG.warn("failed to find solution");
        }
        return prop;
    }

    private Clustering optFlatPartitioning(Dataset<E> dataset, Props prop, ClusteringAlgorithm algorithm, ClusterEvaluation[] evals, int run) {
        Clustering clustering = null;
        Clustering curr;
        int cnt = 0;
        ClusterEvaluation eval = EvaluationFactory.getInstance().getProvider(cliParams.optEval);
        if (algorithm instanceof DBSCAN) {
            double bestEps = 0;
            int bestPts = 0;
            int maxSize = (int) Math.sqrt(dataset.size());
            double maxScore = 0.0, score;
            DBSCANParamEstim<E> dbscanParam = DBSCANParamEstim.getInstance();
            dbscanParam.estimate((Dataset<E>) dataset, prop);

            //plot k-dist
            GnuplotLinePlot<E, C> chart = new GnuplotLinePlot<>(workDir() + File.separatorChar + dataset.getName());
            chart.plot(dbscanParam, dataset, "4-dist plot " + dataset.getName());

            double epsMax = dbscanParam.getMaxEps();
            double epsMin = dbscanParam.getMinEps();
            double step = (epsMax - epsMin) / 10.0;

            System.out.println("min = " + epsMin + ", max = " + epsMax);
            //we have to guess parameters
            double eps;
            LOG.info("using method: {}", cliParams.method);
            switch (cliParams.method) {
                case "sp"://search parameters (a.k.a. super-powers)
                    for (int i = 4; i <= 10; i++) {
                        prop.putInt(DBSCAN.MIN_PTS, i);
                        eps = epsMax;
                        while (eps > epsMin) {
                            prop.putDouble(DBSCAN.EPS, eps);
                            curr = cluster(dataset, prop, algorithm);
                            try {
                                score = eval.score(curr, prop);
                            } catch (ScoreException ex) {
                                score = Double.NaN;
                                LOG.warn("failed to compute score {}: {}", new Object[]{eval.getName(), ex.getMessage()});
                            }
                            System.out.println("eps = " + eps + " minPts = " + i + " => " + eval.getName() + ": " + score + ", clusters: " + curr.size());
                            if (eval.isBetter(score, maxScore)) {
                                maxScore = score;
                                clustering = curr;
                                bestEps = eps;
                                bestPts = i;
                            }
                            export.evaluate(curr, evals, dataset);
                            cnt++;
                            eps -= step; //eps increment
                            if (curr.size() == 1 || curr.size() >= maxSize) {
                                break;
                            }
                        }
                    }
                    break;
                default:
                    bestPts = 4; //fix minPts at 4
                    eps = epsMax;
                    do {
                        prop.putInt(DBSCAN.MIN_PTS, bestPts);
                        while (eps > epsMin) {
                            prop.putDouble(DBSCAN.EPS, eps);
                            curr = cluster(dataset, prop, algorithm);
                            try {
                                score = eval.score(curr, prop);
                            } catch (ScoreException ex) {
                                score = Double.NaN;
                                LOG.warn("failed to compute score {}: {}", new Object[]{eval.getName(), ex.getMessage()});
                            }
                            System.out.println("eps = " + eps + " minPts = " + bestPts + " => " + eval.getName() + ": " + score + ", clusters: " + curr.size());
                            if (eval.isBetter(score, maxScore)) {
                                maxScore = score;
                                clustering = curr;
                                bestEps = eps;
                            }
                            //TODO export will include same result twice
                            export.evaluate(curr, evals, dataset);
                            cnt++;
                            eps -= step; //eps increment
                            if (curr.size() == 1 || curr.size() >= maxSize) {
                                break;
                            }
                        }
                        bestPts++;
                    } while (bestEps == 0.0);
                    break;
            }

            prop.putDouble(DBSCAN.EPS, bestEps);
            prop.putInt(DBSCAN.MIN_PTS, bestPts);
        } else if (algorithm instanceof CURE) {
            //we don't have any heuristic for CURE yet
            double maxScore = 0.0, score;
            double shrink = 0.1;
            double bestShrink = 0;
            while (shrink < 1.0) {
                prop.putDouble(CURE.SHRINK_FACTOR, shrink);
                curr = cluster(dataset, prop, algorithm);
                try {
                    score = eval.score(curr, prop);
                } catch (ScoreException ex) {
                    score = Double.NaN;
                    LOG.warn("failed to compute score {}: {}", new Object[]{eval.getName(), ex.getMessage()});
                }
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
        } else if (algorithm.getName().equals("CLUTO")) {
            String[] configs = new String[]{
                "{crfun=i1,agglofrom=15}",
                "{crfun=i2}",
                "{crfun=e1}",
                "{crfun=g1}",
                "{crfun=g1p}",
                "{crfun=h1,agglofrom=20}",
                "{crfun=h2}",
                "{crfun=slink}",
                "{crfun=wslink}",
                "{crfun=clink}",
                "{crfun=wclink}",
                "{crfun=upgma}"
            };
            Props conf;
            double maxScore = 0.0, score;
            String bestConf = "";
            for (String config : configs) {
                conf = prop.copy();
                conf.merge(Props.fromJson(config));
                curr = cluster(dataset, conf, algorithm);
                try {
                    score = eval.score(curr, conf);
                } catch (ScoreException ex) {
                    score = Double.NaN;
                    LOG.warn("failed to compute score {}: {}", new Object[]{eval.getName(), ex.getMessage()});
                }
                if (eval.isBetter(score, maxScore)) {
                    maxScore = score;
                    clustering = curr;
                    bestConf = config;
                }
                cnt++;
                export.evaluate(curr, evals, dataset);
            }
            LOG.info("best configuration: {}", bestConf);
        } else if (algorithm instanceof AffinityPropagation) {
            /**
             * TODO: a heuristic to determine preference
             */
            String[] configs = new String[]{
                "{damping=0.5}",
                "{damping=0.55}",
                "{damping=0.6}",
                "{damping=0.65}",
                "{damping=0.7}",
                "{damping=0.75}",
                "{damping=0.8}",
                "{damping=0.85}",
                "{damping=0.9}",
                "{damping=0.95}"
            };
            Props conf;
            double maxScore = 0.0, score;
            String bestConf = "";
            for (String config : configs) {
                conf = prop.copy();
                conf.merge(Props.fromJson(config));
                curr = cluster(dataset, conf, algorithm);
                try {
                    score = eval.score(curr, conf);
                } catch (ScoreException ex) {
                    score = Double.NaN;
                    LOG.warn("failed to compute score {}: {}", new Object[]{eval.getName(), ex.getMessage()});
                }
                if (eval.isBetter(score, maxScore)) {
                    maxScore = score;
                    clustering = curr;
                    bestConf = config;
                }
                cnt++;
                export.evaluate(curr, evals, dataset);
            }
            LOG.info("best configuration: {}", bestConf);
        } else {
            clustering = cluster(dataset, prop, algorithm);
        }
        LOG.info("{}: evaluated {} clusterings", new Object[]{algorithm.getName(), cnt});
        return clustering;
    }

    public StopWatch getTimer() {
        return time;
    }

    private ClusterEvaluation[] loadEvaluation(String metrics) {
        ClusterEvaluation[] evals = null;
        if (!metrics.isEmpty()) {
            String[] names = cliParams.eval.split(",");
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

    private void saveHeatmap(CliParams params, Dataset<? extends Instance> dataset, DendrogramMapping mapping) {
        DgViewer panel = new DgViewer();
        panel.setDataset(mapping);
        //pixels per element in matrix
        double mult = 20.0;
        int width = (int) (params.width + dataset.attributeCount() * 40);
        int height = (int) (params.height + dataset.size() * mult);
        LOG.info("resolution {} x {}", new Object[]{width, height});
        BufferedImage image = panel.getBufferedImage(width, height);
        String path = FileUtil.mkdir(workDir() + File.separatorChar + dataset.getName());
        File file = new File(path + File.separatorChar + safeName(params.algorithm) + ".png");
        LOG.info("saving heatmap to {}", file.getAbsolutePath());
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
        String path = cliParams.home + File.separatorChar + cliParams.experiment;
        return FileUtil.mkdir(path);
    }

    private void saveScatter(Clustering clustering, String subdir, ClusteringAlgorithm algorithm) {
        if (cliParams.scatter) {
            String dir = FileUtil.mkdir(workDir() + File.separatorChar + subdir);
            LOG.info("writing scatter to {}", dir);
            GnuplotScatter<Instance, Cluster<Instance>> scatter = new GnuplotScatter<>(dir);
            String title = algorithm.getName() + " - " + clustering.getParams().toString();
            scatter.plot(clustering, title);
        }
    }

    public CliParams getParams() {
        return cliParams;
    }

    /**
     * Read the file and calculate the SHA-1 checksum
     *
     * @param file the file to read
     * @return the hex representation of the SHA-1 using uppercase chars
     * @throws FileNotFoundException if the file does not exist, is a directory
     * rather than a regular file, or for some other reason cannot be opened for
     * reading
     * @throws IOException if an I/O error occurs
     * @throws NoSuchAlgorithmException should never happen
     */
    private static String computeSha1(File file) throws FileNotFoundException,
            IOException, NoSuchAlgorithmException {

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            return new HexBinaryAdapter().marshal(sha1.digest());
        }
    }

}
