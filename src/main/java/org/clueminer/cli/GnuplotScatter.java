/*
 * Copyright (C) 2011-2015 clueminer.org
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

import au.com.bytecode.opencsv.CSVWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.dataset.api.Attribute;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.gnuplot.GnuplotHelper;
import org.clueminer.gnuplot.PointTypeIterator;
import org.clueminer.stats.AttrNumStats;
import org.clueminer.utils.DatasetWriter;
import org.openide.util.Exceptions;

/**
 *
 * @author deric
 * @param <E>
 * @param <C>
 */
public class GnuplotScatter<E extends Instance, C extends Cluster<E>> {

    private final String baseFolder;

    public GnuplotScatter(String folder) {
        this.baseFolder = FileUtil.mkdir(folder);
    }

    public void plot(Clustering<E, C> clustering, String title) {
        Dataset<E> dataset = clustering.getLookup().lookup(Dataset.class);

        String dataDir = getDataDir(baseFolder);
        String dataFile = writeData(dataDir, clustering, dataset);
        int n = triangleSize(dataset.attributeCount());
        String[] plots = new String[n];
        int l = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j <= n; j++) {
                plots[l++] = writePlot(l - 1, title, i, j, clustering, dataFile, dataset);
            }
        }

        try {
            GnuplotHelper.bashPlotScript(plots, baseFolder, "data", "set term pdf font 'Times-New-Roman,8'", "pdf");
            GnuplotHelper.bashPlotScript(plots, baseFolder, "data", "set terminal pngcairo size 800,600 enhanced font 'Verdana,10'", "png");
        } catch (UnsupportedEncodingException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Compute size of triangular matrix (n x n) minus diagonal
     *
     * @param n
     * @return
     */
    protected int triangleSize(int n) {
        return ((n - 1) * n) >>> 1;
    }

    private String getDataDir(String dir) {
        String dataDir = dir + File.separatorChar + "data";
        return FileUtil.mkdir(dataDir);
    }

    private String writePlot(int idx, String title, int x, int y, Clustering<E, C> clustering, String dataFile, Dataset<E> dataset) {
        String dataDir = getDataDir(baseFolder);

        String strn = String.format("%02d", idx);
        String scriptFile = "plot-" + strn + GnuplotHelper.gnuplotExtension;

        try (PrintWriter template = new PrintWriter(dataDir + File.separatorChar + scriptFile, "UTF-8")) {
            template.write(plotTemplate(title, x, y, clustering, dataFile, dataset));
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
            Exceptions.printStackTrace(ex);
        }
        System.out.println("plot file: " + scriptFile);
        return scriptFile;
    }

    private String plotTemplate(String title, int x, int y, Clustering<E, C> clustering, String dataFile, Dataset<E> dataset) {
        int attrCnt = dataset.attributeCount();
        int labelPos = attrCnt + 1;
        //attributes are numbered from zero, gnuplot columns from 1
        double max = dataset.getAttribute(x).statistics(AttrNumStats.MAX);
        double min = dataset.getAttribute(x).statistics(AttrNumStats.MIN);
        String xrange = "[" + min + ":" + max + "]";
        max = dataset.getAttribute(y).statistics(AttrNumStats.MAX);
        min = dataset.getAttribute(y).statistics(AttrNumStats.MIN);
        String yrange = "[" + min + ":" + max + "]";

        String res = "set datafile separator \",\"\n"
                + "set key outside bottom horizontal box\n"
                + "set title \"" + title + "\"\n"
                + "set xlabel \"" + dataset.getAttribute(x).getName() + "\" font \"Times,7\"\n"
                + "set ylabel \"" + dataset.getAttribute(y).getName() + "\" font \"Times,7\"\n"
                + "set xtics 0,0.5 nomirror\n"
                + "set ytics 0,0.5 nomirror\n"
                + "set mytics 2\n"
                + "set mx2tics 2\n"
                + "set xrange " + xrange + "\n"
                + "set yrange " + yrange + "\n"
                + "set grid\n"
                + "set pointsize 0.5\n";
        int i = 0;
        int last = clustering.size() - 1;
        PointTypeIterator pti = new PointTypeIterator();
        for (Cluster clust : clustering) {
            if (i == 0) {
                res += "plot ";
            }
            res += "\"< awk -F\\\",\\\" '{if($" + labelPos + " == \\\"" + clust.getName() + "\\\") print}' " + dataFile + "\" u " + x + ":" + y + " t \"" + clust.getName() + "\" w p pt " + pti.next();
            if (i != last) {
                res += ", \\\n";
            } else {
                res += "\n";
            }

            i++;
        }
        return res;
    }

    /**
     * Write dataset with cluster assignments as labels
     *
     * @param dataDir
     * @param clusters
     * @param dataset
     * @return
     */
    public String writeData(String dataDir, Clustering<E, C> clusters, Dataset<E> dataset) {
        String dataFile = "data-" + dataset.getName() + ".csv";
        try (PrintWriter writer = new PrintWriter(dataDir + File.separatorChar + dataFile, "UTF-8")) {
            CSVWriter csv = new CSVWriter(writer, ',');
            toCsv(csv, clusters, dataset);
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
            Exceptions.printStackTrace(ex);
        }
        return dataFile;
    }

    public void toCsv(DatasetWriter writer, Clustering<E, C> clusters, Dataset<E> dataset) {
        String[] header = new String[dataset.attributeCount() + 1];
        header[dataset.attributeCount()] = "label";
        int i = 0;
        for (Attribute ta : dataset.getAttributes().values()) {
            header[i++] = String.valueOf(ta.getName());
        }
        writer.writeNext(header);
        for (Cluster<E> clust : clusters) {
            for (E inst : clust) {
                writer.writeLine(appendClass(inst, clust.getName()));
            }
        }
    }

    private StringBuilder appendClass(E inst, String klass) {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < inst.size(); i++) {
            if (i > 0) {
                res.append(',');
            }
            res.append(inst.value(i));
        }
        return res.append(',').append(klass);
    }

}
