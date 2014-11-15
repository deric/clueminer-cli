package org.clueminer.cli;

import java.io.File;
import java.io.IOException;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dataset.plugin.ArrayDataset;
import org.clueminer.io.CsvLoader;
import org.clueminer.io.FileHandler;
import org.openide.util.Exceptions;

/**
 *
 * @author Tomas Barton
 */
public class Runner implements Runnable {

    private final Params params;

    Runner(Params p) {
        this.params = p;

    }

    protected Dataset<? extends Instance> parseFile(Params p) throws IOException {
        File f = new File(p.data);
        if (!f.exists() || !f.canRead()) {
            throw new InvalidArgumentException("can't read from file " + p.data);
        }

        Dataset<? extends Instance> dataset = new ArrayDataset(150, 5);
        int clsIndex = p.clsIndex;
        switch (p.type) {
            case "csv":
                CsvLoader csvLoad = new CsvLoader();
                csvLoad.setSeparator(p.separator.charAt(0));
                if (clsIndex > 0) {
                    csvLoad.setClassIndex(clsIndex);
                }
                csvLoad.setHasHeader(p.header);
                csvLoad.load(f, dataset);
                break;
            case "txt":
                if (clsIndex > 0) {
                    FileHandler.loadDataset(f, dataset, p.separator);
                } else {
                    FileHandler.loadDataset(f, dataset, clsIndex, p.separator);
                }
                break;
            default:
                throw new InvalidArgumentException("file format " + p.type + " is not supported");

        }

        if (dataset.isEmpty()) {
            throw new RuntimeException("failed to load any data");
        }

        return dataset;
    }

    @Override
    public void run() {
        try {
            parseFile(params);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

}
