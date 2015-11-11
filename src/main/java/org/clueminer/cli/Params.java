package org.clueminer.cli;

import com.beust.jcommander.Parameter;
import java.io.File;
import org.clueminer.utils.FileUtils;
import org.openide.util.NbBundle;

/**
 *
 * @author Tomas Barton
 */
public class Params {

    @Parameter(names = "--dir", description = "directory for results", required = false)
    public String home = System.getProperty("user.home") + File.separatorChar
            + NbBundle.getMessage(FileUtils.class, "FOLDER_Home");

    @Parameter(names = {"--data", "-d"}, description = "path to dataset", required = true)
    public String data;

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

    @Parameter(names = {"--algorithm", "-a"}, description = "name of the algorithm", required = true)
    public String algorithm;

    @Parameter(names = {"--alg-params", "-p"}, description = "parameters of the algorithm", required = false)
    public String algParams;

    @Parameter(names = "--cluster", description = "run clustering of rows, columns or both", required = false)
    public String cluster = "both";

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

    @Parameter(names = {"--method", "-mth"}, description = "a named method (with predefined configuration)")
    public String method = "";

}
