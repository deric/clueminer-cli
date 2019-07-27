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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import org.clueminer.clustering.api.AlgParams;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.ClusterEvaluation;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.EvaluationTable;
import org.clueminer.clustering.api.InternalEvaluator;
import org.clueminer.clustering.api.Rank;
import org.clueminer.clustering.api.factory.InternalEvaluatorFactory;
import org.clueminer.io.csv.CSVWriter;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.eval.utils.ClusteringComparator;
import org.clueminer.evolution.api.Individual;
import org.clueminer.meta.ranking.ParetoFrontQueue;
import org.clueminer.rank.Correlation;
import org.clueminer.rank.Spearman;
import org.clueminer.utils.PropType;
import org.clueminer.utils.Props;
import org.clueminer.utils.StopWatch;
import org.openide.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.clueminer.clustering.api.RankEvaluator;
import org.clueminer.clustering.api.factory.RankFactory;
import org.clueminer.eval.sort.MORank;

/**
 * Write results into CSV files.
 *
 * @author deric
 * @param <I>
 * @param <E>
 * @param <C>
 */
public class ResultsExporter<I extends Individual<I, E, C>, E extends Instance, C extends Cluster<E>> {

    private static final Logger LOG = LoggerFactory.getLogger(ResultsExporter.class);
    private final Runner<I, E, C> runner;
    private final DecimalFormat df;

    public ResultsExporter(Runner runner) {
        this.runner = runner;
        df = new DecimalFormat();
        df.setGroupingUsed(false);
    }

    public File resultsFile(String fileName) {
        String path = runner.workDir() + File.separatorChar + fileName + ".csv";
        return new File(path);
    }

    public File createNewFile(Dataset<E> dataset, String suffix) {
        File f = resultsFile(dataset.getName() + "-" + suffix);
        if (f.exists()) {
            f.delete();
        }
        return f;
    }

    public void evaluate(Clustering<E, C> clustering, ClusterEvaluation[] evals, Dataset<E> dataset, String suffix) {
        evaluate(clustering, evals, resultsFile(dataset.getName() + "-" + suffix));
    }

    public void evaluate(Clustering<E, C> clustering, ClusterEvaluation[] evals, Dataset<E> dataset) {
        evaluate(clustering, evals, resultsFile(dataset.getName()));
    }

    public void writeMeta(HashMap<String, Double> meta, Dataset<E> dataset) {
        File results = resultsFile(dataset.getName() + "-meta");
        String[] row = new String[meta.size() + 1];
        int i = 0;
        row[i] = "dataset";
        for (String s : meta.keySet()) {
            row[++i] = s;
        }
        writeCsvLine(results, row, false);
        row = new String[meta.size() + 1];
        i = 0;
        row[i] = dataset.getName();
        for (double d : meta.values()) {
            row[++i] = df.format(d);
        }
        writeCsvLine(results, row, true);
    }

    public void ranking(SortedMap<Double, Clustering<E, C>> ranking, ClusterEvaluation[] evals, File results) {
        HashMap<String, String> meta = new HashMap<>();
        for (Entry<Double, Clustering<E, C>> e : ranking.entrySet()) {
            Clustering<E, C> clustering = e.getValue();
            Dataset<E> dataset = clustering.getLookup().lookup(Dataset.class);
            if (dataset == null) {
                throw new RuntimeException("dataset not in clustering lookup!");
            }

            meta.put("dataset", dataset.getName());
            meta.put("clusters", String.valueOf(clustering.size()));
            meta.put("rank", df.format(e.getKey()));
            meta.put("algorithm", clustering.getParams().get(AlgParams.ALG));

            evaluate(clustering, evals, results, meta);
        }
    }

    /**
     * Evaluates different ranking approaches
     *
     * @param list
     * @param results
     * @param supervised
     */
    public void evaluateRankings(List<Clustering<E, C>> list, File results, ClusterEvaluation supervised) {
        RankFactory rf = RankFactory.getInstance();
        RankEvaluator rankCmp = new Correlation();
        HashMap<Integer, Integer> map = new HashMap<>(list.size());
        //internal evaluation
        InternalEvaluatorFactory ief = InternalEvaluatorFactory.getInstance();

        ClusteringComparator comp = new ClusteringComparator(supervised);
        Clustering[] clusts = new Clustering[list.size()];
        Clustering[] ref = new Clustering[list.size()];
        int i = 0;
        for (Clustering<E, C> c : list) {
            clusts[i] = c;
            ref[i] = c;
            i++;
        }
        Arrays.sort(ref, comp);
        ClusterEvaluation[] evals = ief.getAllArray();
        List<ClusterEvaluation<E, C>> obj = new LinkedList<>();

        for (Rank rank : rf.getAllArray()) {
            LOG.info("ranking using {}", rank.getName());
            obj.clear();
            obj.add(evals[0]);
            switch (rank.getMinObjectives()) {
                case 1:
                    for (i = 0; i < evals.length; i++) {
                        obj.set(0, evals[i]);
                        evaluateRanking(clusts, ref, results, rankCmp, rank, obj, map, supervised);
                    }
                    break;
                case 2:
                    obj.add(evals[0]);
                    for (i = 0; i < evals.length - 1; i++) {
                        obj.set(0, evals[i]);
                        for (int j = i + 1; j < evals.length; j++) {
                            obj.set(1, evals[j]);
                            evaluateRanking(clusts, ref, results, rankCmp, rank, obj, map, supervised);
                        }
                    }
                    break;
                case 3:
                    obj.add(evals[0]);
                    obj.add(evals[0]);
                    for (i = 0; i < evals.length - 2; i++) {
                        obj.set(0, evals[i]);
                        for (int j = i + 1; j < evals.length - 1; j++) {
                            obj.set(1, evals[j]);
                            for (int k = j + 1; k < evals.length; k++) {
                                obj.set(2, evals[k]);
                                evaluateRanking(clusts, ref, results, rankCmp, rank, obj, map, supervised);
                            }
                        }
                    }
                    break;
                default:
                    throw new RuntimeException("not supported. min obj = " + rank.getMinObjectives());
            }
        }
    }

    public void clusterings(List<Clustering<E, C>> list, File results) {

        Clustering<E, C> c;
        for (int i = 0; i < list.size(); i++) {
            c = list.get(i);
            Map<String, String> res = new TreeMap<>();
            res.put("size", String.valueOf(c.size()));
            res.put("fingerprint", c.fingerprint());
            res.put("params", c.getParams().toString());

            //write header
            if (!results.exists()) {
                writeCsvLine(results, res.keySet().toArray(new String[0]), false);
            }
            writeCsvLine(results, res.values().toArray(new String[0]), true);
        }

    }

    /**
     * Write correlation to a supervised ranking to a file
     *
     * @param clusts
     * @param ref
     * @param results
     * @param rankCmp
     * @param rank
     * @param obj
     * @param map
     */
    private void evaluateRanking(Clustering[] clusts, Clustering[] ref,
            File results, RankEvaluator rankCmp, Rank rank,
            List<ClusterEvaluation<E, C>> obj, HashMap<Integer, Integer> map,
            ClusterEvaluation supervised) {

        try {
            if (rank instanceof MORank) {
                shuffleArray(clusts);
            }
            clusts = rank.sort(clusts, obj);
            Map<String, String> res = new TreeMap<>();
            String methodName = rankingStrategyName(rank, obj);
            double corr = rankCmp.correlation(clusts, ref, map);
            //LOG.info("{}: {}", methodName, corr);
            res.put("method", methodName);
            res.put("ext-ranking", supervised.getName());
            res.put("num-objectives", String.valueOf(obj.size()));
            res.put("ranking", rank.getName());
            res.put(rankCmp.getName(), df.format(corr));

            //write header
            if (!results.exists()) {
                writeCsvLine(results, res.keySet().toArray(new String[0]), false);
            }
            writeCsvLine(results, res.values().toArray(new String[0]), true);
        } catch (Exception e) {
            LOG.error("rakning failed ", e.getMessage(), e);
        }
    }

    // Implementing Fisher–Yates shuffle
    private void shuffleArray(Clustering[] ar) {
        Random rnd = ThreadLocalRandom.current();
        for (int i = ar.length - 1; i > 0; i--) {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            Clustering a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

    private String rankingStrategyName(Rank rank, List<ClusterEvaluation<E, C>> obj) {
        StringBuilder sb = new StringBuilder();

        sb.append(rank.getName()).append("-");
        for (int i = 0; i < obj.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            ClusterEvaluation ev = obj.get(i);
            if (ev instanceof InternalEvaluator) {
                InternalEvaluator eval = (InternalEvaluator) ev;
                sb.append(eval.getCallsign());
            } else {
                sb.append(ev.getHandle());
            }

        }
        return sb.toString();
    }

    /**
     * Compute correlation between supervised ranking and unsupervised ones.
     *
     * @param ranking multi-objective ranking
     * @param evals
     * @param results
     * @param supervised reference sorting
     * @param methodName
     * @param params
     */
    public void correlation(SortedMap<Double, Clustering<E, C>> ranking, ClusterEvaluation[] evals, File results, ClusterEvaluation supervised, String methodName, Props params) {
        RankEvaluator rankCmp = new Correlation();
        HashMap<Integer, Integer> map = new HashMap<>(ranking.size());
        ClusteringComparator comp = new ClusteringComparator(supervised);
        Clustering[] mo = new Clustering[ranking.size()];
        Clustering[] ref = new Clustering[ranking.size()];
        int i = 0;
        for (Clustering c : ranking.values()) {
            mo[i] = c;
            ref[i] = c;
            i++;
        }
        Arrays.sort(ref, comp);
        /* System.out.println("Reference:");
         * for (int j = 0; j < ref.length; j++) {
         * System.out.println(j + ": " + comp.evaluationTable(ref[j]).getScore(supervised) + ", ID: " + ref[j].getId());;
         * //Clustering clustering = ref[j];
         * }
         * System.out.println("MO!");
         * for (Entry<Double, Clustering<E, C>> e : ranking.entrySet()) {
         * System.out.println(e.getKey() + ": " + comp.evaluationTable(e.getValue()).getScore(supervised));
         * //Clustering clustering = ref[j];
         * } */
        double corr = rankCmp.correlation(mo, ref, map);
        LOG.info("{}: {}", methodName, corr);

        Map<String, String> res = new TreeMap<>();
        res.put(methodName, df.format(corr));

        for (ClusterEvaluation e : evals) {
            comp.setEvaluator(e);
            Arrays.sort(mo, comp);
            corr = rankCmp.correlation(mo, ref, map);
            res.put(e.getName(), df.format(corr));
        }
        evaluateMOrank(res, ref, rankCmp, params);

        //write header
        if (!results.exists()) {
            writeCsvLine(results, res.keySet().toArray(new String[0]), false);
        }
        writeCsvLine(results, res.values().toArray(new String[0]), true);
    }

    /**
     * Evaluate clustering ranking to a reference objective
     *
     * @param res
     * @param ref
     * @param rankCmp
     * @param params
     */
    private void evaluateMOrank(Map<String, String> res, Clustering[] ref, RankEvaluator rankCmp, Props params) {
        InternalEvaluatorFactory ef = InternalEvaluatorFactory.getInstance();
        ClusterEvaluation[] objectives;
        //e.g. "BIC,PointBiserial-Norm,Ratkowsky-Lance"
        String criteria = params.get("mo-criteria", "all");
        if (criteria.equals("all")) {
            objectives = ef.getAllArray();
        } else {
            String[] crit = criteria.split(",");
            objectives = new ClusterEvaluation[crit.length];
            for (int i = 0; i < crit.length; i++) {
                objectives[i] = (ClusterEvaluation) ef.getProvider(crit[i]);
            }
        }

        Clustering[] mo = new Clustering[ref.length];
        int numFronts = params.getInt("fronts", 20);
        ClusterEvaluation sort;
        double maxCorr = -1.0;
        for (int i = 0; i < objectives.length; i++) {
            for (int j = i + 1; j < objectives.length; j++) {
                for (int k = 0; k < objectives.length; k++) {
                    List<ClusterEvaluation<E, C>> moObj = new LinkedList<>();
                    //first MO criteria
                    moObj.add(objectives[i]);
                    //second criteria
                    moObj.add(objectives[j]);
                    //sort criteria
                    sort = objectives[k];
                    LOG.trace("{} & {} & {}, num fronts: {}", objectives[i].getName(),
                            objectives[j].getName(), objectives[k].getName(), numFronts);
                    ParetoFrontQueue<E, C, Clustering<E, C>> q = new ParetoFrontQueue<>(numFronts, moObj, sort);
                    for (Clustering<E, C> c : ref) {
                        q.add(c);
                    }
                    LOG.trace("excluded clusterings: {}", q.getExcludedSize());
                    SortedMap<Double, Clustering<E, C>> ranking = q.computeRanking();

                    int l = 0;
                    for (Clustering<E, C> c : ranking.values()) {
                        mo[l++] = c;
                    }
                    if (ranking.size() != ref.length) {
                        LOG.warn("ranking size: {} vs reference: {}", ranking.size(), ref.length);
                    }
                    HashMap<Integer, Integer> map = new HashMap<>(ref.length);
                    double corr = rankCmp.correlation(mo, ref, map);
                    String moName = moName(q);
                    LOG.info("{}: {}", moName, corr);
                    if (corr > maxCorr) {
                        maxCorr = corr;
                        LOG.info("best result so far: {}: {}", moName, corr);
                    }
                    res.put(moName, df.format(corr));
                }
            }
        }
    }

    private String moName(ParetoFrontQueue<E, C, Clustering<E, C>> q) {
        StringBuilder sb = new StringBuilder();
        q.getObjectives().forEach((ev) -> {
            sb.append(ev.getName()).append(" & ");
        });
        sb.append(q.getSortingObjectives().getName());
        return sb.toString();
    }

    /**
     * Export results to CSV
     *
     * @param clustering will be exported one per row in CSV
     * @param evals
     * @param results
     * @param meta extra meta attributes
     */
    public void evaluate(Clustering<E, C> clustering, ClusterEvaluation[] evals, File results, HashMap<String, String> meta) {
        String[] line;
        int extraAttr = meta.size() + 3;
        double score;
        StopWatch etime = new StopWatch(false);

        //CSV header
        //logger.log(Level.INFO, "writing results into: {0}", results.getAbsolutePath());
        if (!results.exists()) {
            line = new String[evals.length + extraAttr];
            int i = 0;
            for (String val : meta.keySet()) {
                line[i++] = val;
            }
            line[i++] = "time (ms)";
            for (ClusterEvaluation e : evals) {
                line[i++] = e.getName();
            }
            line[i++] = "template";
            line[i++] = "alg time";
            writeCsvLine(results, line, false);
        }

        // csv stats for computing evaluation metrics
        File criteriaCSV = new File(results.getParent() + File.separatorChar + "criteria.csv");
        LOG.trace("criteria stats written to: {}", criteriaCSV.getAbsolutePath());
        if (!criteriaCSV.exists()) {
            line = new String[4];
            line[0] = "objective";
            line[1] = "time";
            line[2] = "k";
            line[3] = "fingerprint";

            writeCsvLine(criteriaCSV, line, false);
        }

        line = new String[evals.length + extraAttr];
        int i = 0;
        for (String m : meta.values()) {
            line[i++] = m;
        }
        StopWatch time = clustering.getLookup().lookup(StopWatch.class);
        if (time != null) {
            line[i++] = df.format(time.timeInMs());
        } else {
            line[i++] = "";
        }
        LOG.info("Evaluating scores " + clustering.fingerprint());
        ClusteringComparator comp = new ClusteringComparator();
        EvaluationTable et = comp.evaluationTable(clustering);
        String[] criteria = new String[4];
        try {
            for (ClusterEvaluation e : evals) {
                etime.startMeasure();
                score = et.getScore(e);
                etime.endMeasure();
                line[i++] = String.valueOf(score);
                System.out.print(".");
                //export only external criteria
                if (!e.isExternal()) {
                    criteria[0] = e.getName();
                    criteria[1] = etime.formatMs();
                    criteria[2] = String.valueOf(clustering.size());
                    criteria[3] = clustering.fingerprint();
                    writeCsvLine(criteriaCSV, criteria, true);
                }
            }
        } catch (Exception e) {
            System.out.println("clustering " + clustering.getParams().toJson());
            Exceptions.printStackTrace(e);
        }
        System.out.println("");
        line[i++] = clustering.getParams().toJson();
        writeCsvLine(results, line, true);
    }

    /**
     * Export results to CSV
     *
     * @param clustering
     * @param evals
     * @param results
     */
    public void evaluate(Clustering<E, C> clustering, ClusterEvaluation[] evals, File results) {
        if (clustering == null) {
            throw new RuntimeException("ERROR: missing clustering: " + clustering);
        }
        if (evals == null) {
            return;
        }
        Dataset<E> dataset = clustering.getLookup().lookup(Dataset.class);
        if (dataset == null) {
            throw new RuntimeException("dataset not in clustering lookup!");
        }
        String[] line;
        int extraAttr = 5;
        double score;

        //header
        //logger.log(Level.INFO, "writing results into: {0}", results.getAbsolutePath());
        if (!results.exists()) {
            line = new String[evals.length + extraAttr];
            int i = 0;
            line[i++] = "dataset";
            line[i++] = "clusters";
            line[i++] = "time (ms)";
            for (ClusterEvaluation e : evals) {
                line[i++] = e.getName();
            }
            line[i++] = "template";
            line[i++] = "alg time";
            writeCsvLine(results, line, false);
        }

        line = new String[evals.length + extraAttr];
        int i = 0;
        line[i++] = dataset.getName();
        line[i++] = String.valueOf(clustering.size());
        StopWatch time = clustering.getLookup().lookup(StopWatch.class);
        if (time != null) {
            line[i++] = df.format(time.timeInMs());
            LOG.info("run time = " + time.timeInSec() + "s");
        } else {
            line[i++] = "";
        }
        if (evals.length == 0) {
            LOG.warn("no evaluation method specified");
        } else {
            LOG.info("Evaluating scores for " + clustering.fingerprint());
            ClusteringComparator comp = new ClusteringComparator();
            EvaluationTable et = comp.evaluationTable(clustering);
            try {
                for (ClusterEvaluation e : evals) {
                    score = et.getScore(e);
                    line[i++] = String.valueOf(score);
                    LOG.debug(e.getName() + ": " + score);
                }
            } catch (Exception e) {
                LOG.info("clustering {}", clustering.getParams().toJson());
                Exceptions.printStackTrace(e);
            }
            System.out.println("");
        }
        line[i++] = clustering.getParams().toJson();
        Props p = clustering.getParams();
        line[i++] = String.valueOf(p.get(PropType.PERFORMANCE, "time", -1));
        writeCsvLine(results, line, true);
    }

    public void writeCsvLine(File file, Double[] columns, boolean apend) {
        String[] cols = new String[columns.length];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = String.valueOf(columns[i]);
        }
        writeCsvLine(file, cols, apend);
    }

    /**
     * Write a line into given CSV file
     *
     * @param file
     * @param columns
     * @param apend
     */
    public void writeCsvLine(File file, String[] columns, boolean apend) {
        try (PrintWriter writer = new PrintWriter(
                new FileOutputStream(file, apend)
        )) {

            CSVWriter csv = new CSVWriter(writer, runner.getParams().separator.charAt(0));
            csv.writeNext(columns, false);
            writer.close();

        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public void writeCsvLine(File file, StringBuilder line, boolean apend) {
        try (PrintWriter writer = new PrintWriter(
                new FileOutputStream(file, apend)
        )) {

            CSVWriter csv = new CSVWriter(writer, runner.getParams().separator.charAt(0));
            csv.writeLine(line);
            writer.close();
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public void exportFront(SortedMap<Double, Clustering<E, C>> ranking, ClusterEvaluation[] evals, File results) {
        String sep = ",";
        double rank;
        String alg;
        Clustering<E, C> c;
        StringBuilder sb = new StringBuilder();
        sb.append("rank").append(sep);
        sb.append("algorithm").append(sep);
        for (ClusterEvaluation eval : evals) {
            sb.append(eval.getName()).append(sep);
        }
        sb.append("front").append(sb);
        sb.append("clusters");
        //write header (overwrite previous result)
        writeCsvLine(results, sb, false);
        for (Entry<Double, Clustering<E, C>> e : ranking.entrySet()) {
            sb = new StringBuilder();
            rank = e.getKey();
            c = e.getValue();
            alg = c.getParams().get(AlgParams.ALG);
            sb.append(format(rank)).append(" - ").append(alg).append("[").append(c.size()).append("]").append(sep)
                    .append(alg).append(sep);
            ClusteringComparator comp = new ClusteringComparator();
            EvaluationTable et = comp.evaluationTable(e.getValue());
            for (ClusterEvaluation eval : evals) {
                sb.append(format(et.getScore(eval))).append(sep);
            }
            sb.append(String.format("%.0f", rank)).append(sep);
            sb.append(c.size());
            writeCsvLine(results, sb, true);
        }
    }

    private String format(double val) {
        return String.format("%.2f", val);
    }

}
