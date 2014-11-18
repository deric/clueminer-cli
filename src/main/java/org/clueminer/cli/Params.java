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

    @Parameter(names = "--data", description = "path to dataset", required = true)
    public String data;

    @Parameter(names = "--type", description = "type of data (csv, txt, arff)", required = false)
    public String type = "csv";

    @Parameter(names = "--separator", description = "separator of columns", required = false)
    public String separator = ",";

    @Parameter(names = "--class", description = "index of class column", required = false)
    public int clsIndex = -1;

    @Parameter(names = "--header", description = "if there is a header with attribute name", required = false)
    public boolean header = true;

    @Parameter(names = "--algorithm", description = "name of the algorithm", required = true)
    public String algorithm;

    @Parameter(names = "--alg-params", description = "parameters of the algorithm", required = false)
    public String algParams;

    @Parameter(names = "--cluster", description = "run clustering of rows, columns or both", required = false)
    public String cluster = "both";

    @Parameter(names = "--tree", description = "print dendrogram tree", required = false)
    public boolean tree = false;

    @Parameter(names = "--matrix", description = "print similarity matrix", required = false)
    public boolean matrix = false;

}
