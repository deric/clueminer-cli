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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * Simple CLI for running clustering algorithms.
 *
 * @author deric
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Params p = parseArguments(args);
        Runner runner = new Runner(p);
        runner.run();
    }

    protected static Params parseArguments(String[] args) {
        Params params = new Params();
        JCommander cmd = new JCommander(params);
        printUsage(args, cmd, params);
        return params;
    }

    public static void printUsage(String[] args, JCommander cmd, Params params) {
        if (args.length == 0) {
            StringBuilder sb = new StringBuilder();
            cmd.usage(sb);
            sb.append("\n").append("attributes marked with * are mandatory");
            System.out.println(sb);
            System.err.println("missing mandatory arguments");
            System.exit(0);
        }
        try {
            cmd.parse(args);
            /**
             * TODO validate values of parameters
             */

        } catch (ParameterException ex) {
            System.out.println(ex.getMessage());
            cmd.usage();
            System.exit(0);
        }
    }

}
