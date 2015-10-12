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
import org.clueminer.cli.FileUtil;

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

}
