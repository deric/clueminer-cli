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
package org.clueminer.plot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import org.clueminer.clustering.algorithm.DBSCANParamEstim;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.gnuplot.GnuplotHelper;
import static org.clueminer.gnuplot.GnuplotHelper.gnuplotExtension;
import org.openide.util.Exceptions;

/**
 *
 * @author deric
 */
public class GnuplotLinePlot<E extends Instance, C extends Cluster<E>> extends BasePlot {

    public GnuplotLinePlot(String base) {
        super(base);
    }

    public void plot(DBSCANParamEstim<E> dbscanParam, Dataset<E> dataset, String title) {
        String absDir = getDataDir(baseFolder);
        String dataFile = writeData(absDir, dbscanParam, dataset);
        String script = "k-dist";
        GnuplotHelper.writeGnuplot(absDir, script, generatePlot(title, 1, 2, dbscanParam.getKnee(), dataFile));

        String[] plots = new String[1];
        plots[0] = script + gnuplotExtension;

        try {
            bashPlotScript(plots, baseFolder, dataFolder, "set term pdf font 'Times-New-Roman,8'", "pdf");
            bashPlotScript(plots, baseFolder, dataFolder, "set terminal pngcairo size 800,600 enhanced font 'Verdana,10'", "png");
        } catch (UnsupportedEncodingException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private String writeData(String dataDir, DBSCANParamEstim<E> dbscanParam, Dataset<E> dataset) {
        double slope = dbscanParam.getSlope();
        Double[] dist = dbscanParam.getKdist();

        File file = new File(dataDir + File.separatorChar + "kdist_data.csv");
        try (PrintWriter writer = new PrintWriter(
                new FileOutputStream(file, false)
        )) {
            for (int i = 0; i < dataset.size() - 1; i++) {
                writer.write(i + "," + dist[i] + "," + dbscanParam.ref(dist, i, slope) + "\n");
            }
            writer.close();

        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
        return file.getName();
    }

    private String generatePlot(String title, int x, int y, int max, String dataFile) {
        String res = "set datafile separator \",\"\n"
                + "set key outside bottom horizontal box\n"
                + "set title \"" + title + "\"\n"
                + "set xlabel \"order\" font \"Times,12\"\n"
                + "set ylabel \"k-dist\" font \"Times,12\"\n"
                //   + "set xtics 0,0.5 nomirror\n"
                //   + "set ytics 0,0.5 nomirror\n"
                + "set mytics 2\n"
                + "set mx2tics 2\n"
                + "set xtics add ('knee=" + max + "' " + max + ")\n"
                + "set arrow from " + max + ", graph 0 to " + max + ", graph 1 nohead ls 4 lw 2\n"
                + "set grid\n"
                + "set pointsize 0.5\n";

        res += "plot '" + dataFile + "' u " + x + ":" + y + " title '4-dist' with linespoints linewidth 2 pointtype 7 pointsize 0.3,\\\n"
                + "'' u 1:3 title 'kx' with linespoints linewidth 1 pointtype 8 pointsize 0.3";
        return res;
    }
}
