package org.clueminer.cli;

import java.io.IOException;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.fixtures.CommonFixture;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author deric
 */
public class RunnerTest {

    private final CommonFixture cf = new CommonFixture();
    private Runner subject;
    private final Params p = new Params();

    public RunnerTest() {
    }

    @Before
    public void setUp() throws IOException {
        p.data = cf.irisData().getAbsolutePath();
        subject = new Runner(p);
    }

    @Test
    public void testParseCsvFile() throws Exception {
        p.clsIndex = 4;
        p.type = "csv";
        p.header = false;
        Dataset<? extends Instance> dataset = subject.parseFile(p);
        assertEquals(150, dataset.size());
        assertEquals(4, dataset.attributeCount());
        assertEquals(3, dataset.getClasses().size());
    }

    @Test
    public void testParseFile() throws Exception {
        p.clsIndex = 4;
        p.header = false;
        p.type = "txt";
        Dataset<? extends Instance> dataset = subject.parseFile(p);
        assertEquals(150, dataset.size());
        //TODO attributes won't be created by this parser
        //assertEquals(4, dataset.attributeCount());
        //assertEquals(3, dataset.getClasses().size());
    }

    @Test
    public void testParseArffFile() throws Exception {
        p.data = cf.irisArff().getAbsolutePath();
        p.clsIndex = -1;
        p.type = "arff";
        p.header = false;
        Dataset<? extends Instance> dataset = subject.parseFile(p);
        assertEquals(150, dataset.size());
        assertEquals(4, dataset.attributeCount());
        assertEquals(3, dataset.getClasses().size());
    }

}
