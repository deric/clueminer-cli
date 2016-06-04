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

/**
 *
 * @author deric
 */
public class FileUtil {

    public static String mkdir(String folder) {
        File file = new File(folder);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new RuntimeException("Failed to create " + folder + " !");
            }
        }
        return file.getAbsolutePath();
    }

    /**
     * Create directory inside working dir
     *
     * @param name
     * @param params
     * @return
     */
    public static String ensureDir(String name, Params params) {
        if (name == null || name.isEmpty()) {
            RandomString rand = new RandomString(8);
            name = rand.nextString();
        }
        String path = params.home + File.separatorChar + name;
        return FileUtil.mkdir(path);
    }

}
