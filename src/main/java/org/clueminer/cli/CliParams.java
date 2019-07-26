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

import com.beust.jcommander.Parameter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.clueminer.utils.FileUtils;
import org.openide.util.NbBundle;

/**
 * Command line arguments
 * *
 * @author Tomas Barton
 */
public class CliParams {

    @Parameter(names = "--dir", description = "directory for results", required = false)
    public String home = System.getProperty("user.home") + File.separatorChar
            + NbBundle.getMessage(FileUtils.class, "FOLDER_Home");

    @Parameter(names = {"--data", "-d"}, description = "path to dataset", required = false)
    public String data;

    @Parameter(names = {"--generate", "-g"}, description = "generate data e.g. 100x5", required = false)
    public String generate;

    @Parameter(names = {"--type", "-t"}, description = "type of data (csv, txt, arff)", required = false)
    public String type;

    @Parameter(names = "--separator", description = "separator of columns", required = false)
    public String separator = ",";

    @Parameter(names = "--class", description = "index of class column", required = false)
    public int clsIndex = -1;

    @Parameter(names = "--id", description = "index of ID column", required = false)
    public int idIndex = -1;

    @Parameter(names = "--skip", description = "commma separated list of indexes to skip", required = false)
    public String skip = null;

    @Parameter(names = "--header", description = "if there is a header with attribute name", required = false)
    public boolean header = true;

    @Parameter(names = {"--algorithm", "-a"}, description = "name of the algorithm", required = false)
    public String algorithm;

    @Parameter(names = "--cluster", description = "run clustering of rows, columns or both", required = false)
    public String cluster = "rows";

    @Parameter(names = "--tree", description = "print dendrogram tree", required = false)
    public boolean tree = false;

    @Parameter(names = "--matrix", description = "print similarity matrix", required = false)
    public boolean matrix = false;

    @Parameter(names = "--heatmap", description = "save heatmap to file", required = false)
    public boolean heatmap = false;

    @Parameter(names = "--cutoff", description = "Cutoff strategy", required = false)
    public String cutoff = "hill-climb inc";

    @Parameter(names = "--width", description = "minimal width", required = false)
    public int width = 200;

    @Parameter(names = "--height", description = "minimal height", required = false)
    public int height = 200;

    @Parameter(names = {"--eval", "-e"}, description = "internal/external evaluation of resut; provide comma-separated metric names, e.g. 'NMI-sqrt,ARI'")
    public String eval = "";

    @Parameter(names = "--hint-k", description = "provide K parameter based on number of classes in the dataset")
    public boolean hintK = false;

    @Parameter(names = {"--repeat", "-r"}, description = "number of repeated runs of the algorithm")
    public int repeat = 1;

    @Parameter(names = {"--scatter", "-s"}, description = "save resulting scatterplot")
    public boolean scatter = false;

    @Parameter(names = {"--experiment", "-exp"}, description = "experiment name, will use {base dir}/{experiment} (by default algorithm name)")
    public String experiment;

    @Parameter(names = {"--optimal", "-opt"}, description = "try to find optimal configuration for the algorithm")
    public boolean optimal = false;

    @Parameter(names = {"--opt-eval", "-oe"}, description = "metric for choosing optimal clustring (internal or external)")
    public String optEval = "NMI-sqrt";

    @Parameter(names = {"--method", "-m th"}, description = "a named method (with predefined configuration)")
    public String method = "";

    @Parameter(names = {"--meta-search", "-mts"}, description = "Tries to find optimal algorithm for given data")
    public boolean metaSearch = false;

    @Parameter(names = {"--se"}, description = "State-space exploration algorithm")
    public String se = null;

    @Parameter(names = {"--log", "-l"}, description = "Log level: WARNING, SEVERE, FINE, FINER, FINEST")
    public String logLevel = "info";

    @Parameter(names = {"--exec"}, description = "Executor, default: 'local' or provide cluster URI in order to execute jobs remotely")
    public String executor = "local";

    @Parameter(names = {"--sync-db"}, description = "Synchronize meta-db tables with Clueminer definitions. Works only with Mesos executor.")
    public boolean syncDB = false;

    /**
     * A hack to support passing json containing whitespaces
     */
    @Parameter(names = {"--alg-params", "-p"}, variableArity = true, description = "parameters of the algorithm as JSON (or path to JSON file)", splitter = NoopSplitter.class)
    public List<String> params = new ArrayList<>();
    private String p;

    @Parameter(names = {"--meta-params", "-m"}, variableArity = true, description = "meta search configuration", splitter = NoopSplitter.class)
    public List<String> metaParams = new ArrayList<>();

    private String meta;

    public String getParams() {
        if (p == null) {
            p = concat(params);
        }
        return p;
    }

    public String getMetaParams() {
        if (meta == null) {
            meta = concat(metaParams);
        }
        return meta;
    }

    private String concat(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s).append(" ");
        }
        return sb.toString();
    }

}
