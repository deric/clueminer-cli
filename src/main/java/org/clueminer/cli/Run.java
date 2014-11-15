package org.clueminer.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import java.util.concurrent.Executors;

/**
 *
 * @author deric
 */
public class Run {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Params p = parseArguments(args);
        Runner runner = new Runner(p);

        Executors.newSingleThreadExecutor().execute(runner);
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
