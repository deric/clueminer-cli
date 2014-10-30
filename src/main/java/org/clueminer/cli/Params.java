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

    @Parameter(names = "--algorithm", description = "name of algorithm", required = true)
    public String algorithm;
}
