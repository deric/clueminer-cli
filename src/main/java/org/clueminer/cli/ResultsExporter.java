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
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.logging.Logger;
import org.clueminer.clustering.api.AlgParams;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.ClusterEvaluation;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.csv.CSVWriter;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.evolution.api.Individual;
import org.clueminer.utils.StopWatch;
import org.openide.util.Exceptions;

/**
 * Write results into CSV files.
 *
 * @author deric
 * @param <I>
 * @param <E>
 * @param <C>
 */
public class ResultsExporter<I extends Individual<I, E, C>, E extends Instance, C extends Cluster<E>> {

    private static final Logger LOGGER = Logger.getLogger(ResultsExporter.class.getName());
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
     * Export results to CSV
     *
     * @param clustering will be exported one per row in CSV
     * @param evals
     * @param results
     * @param meta       extra meta attributes
     */
    public void evaluate(Clustering<E, C> clustering, ClusterEvaluation[] evals, File results, HashMap<String, String> meta) {
        String[] line;
        int extraAttr = meta.size() + 2;
        double score;

        //header
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
            writeCsvLine(results, line, false);
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
        System.out.print("Evaluating scores " + clustering.fingerprint());
        try {
            for (ClusterEvaluation e : evals) {
                score = e.score(clustering);
                line[i++] = String.valueOf(score);
                System.out.print(".");
                //System.out.println(e.getName() + ": " + score);
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
        int extraAttr = 4;
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
            writeCsvLine(results, line, false);
        }

        line = new String[evals.length + extraAttr];
        int i = 0;
        line[i++] = dataset.getName();
        line[i++] = String.valueOf(clustering.size());
        StopWatch time = clustering.getLookup().lookup(StopWatch.class);
        if (time != null) {
            line[i++] = df.format(time.timeInMs());
        } else {
            line[i++] = "";
        }
        System.out.print("Evaluating scores " + clustering.fingerprint());
        try {
            for (ClusterEvaluation e : evals) {
                score = e.score(clustering);
                line[i++] = String.valueOf(score);
                System.out.print(".");
                //System.out.println(e.getName() + ": " + score);
            }
        } catch (Exception e) {
            System.out.println("clustering " + clustering.getParams().toJson());
            Exceptions.printStackTrace(e);
        }
        System.out.println("");
        line[i++] = clustering.getParams().toJson();
        writeCsvLine(results, line, true);
    }

    public void writeCsvLine(File file, Double[] columns, boolean apend) {
        String[] cols = new String[columns.length];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = String.valueOf(columns[i]);
        }
        writeCsvLine(file, cols, apend);
    }

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
            for (ClusterEvaluation eval : evals) {
                sb.append(format(eval.score(c))).append(sep);
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
