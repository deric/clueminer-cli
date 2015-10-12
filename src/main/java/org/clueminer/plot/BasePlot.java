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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import org.clueminer.cli.FileUtil;
import org.clueminer.gnuplot.GnuplotHelper;
import static org.clueminer.gnuplot.GnuplotHelper.gnuplotExtension;

/**
 *
 * @author deric
 */
public class BasePlot {

    protected String baseFolder;
    protected String dataFolder = "data";

    public BasePlot(String folder) {
        this.baseFolder = FileUtil.mkdir(folder);
    }

    public String getDataDir(String dir) {
        String dataDir = dir + File.separatorChar + dataFolder;
        return FileUtil.mkdir(dataDir);
    }

    /**
     *
     * @param plots plot names without extension
     * @param dir base dir
     * @param gnuplotDir directory with gnuplot file
     * @param term
     * @param ext extentions of output format
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public static void bashPlotScript(String[] plots, String dir, String gnuplotDir, String term, String ext)
            throws FileNotFoundException, UnsupportedEncodingException, IOException {
        //bash script to generate results
        String shFile = dir + File.separatorChar + "_plot-" + ext;
        try (PrintWriter template = new PrintWriter(shFile, "UTF-8")) {
            template.write(GnuplotHelper.bashTemplate(gnuplotDir));
            template.write("TERM=\"" + term + "\"\n");
            int pos;
            for (String plot : plots) {
                pos = plot.indexOf(".");
                if (pos > 0) {
                    //remove extension part
                    plot = plot.substring(0, pos);
                }
                template.write("gnuplot -e \"${TERM}\" " + "$PWD" + File.separatorChar + plot + gnuplotExtension
                        + " > $PWD" + File.separatorChar + ".." + File.separatorChar + plot + "." + ext + "\n");
            }
        }
        Runtime.getRuntime().exec("chmod u+x " + shFile);
    }

}
