package org.clueminer.cli;

import java.io.IOException;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.fixtures.CommonFixture;
import org.junit.After;
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
    private Params p = new Params();

    public RunnerTest() {
    }

    @Before
    public void setUp() throws IOException {
        p.data = cf.irisData().getAbsolutePath();
        subject = new Runner(p);

    }

    @After
    public void tearDown() {
    }

    @Test
    public void testParseFile() throws Exception {
        p.clsIndex = 4;
        p.header = false;
        Dataset<? extends Instance> dataset = subject.parseFile(p);
        System.out.println("dataset:" + dataset.toString());
        assertEquals(150, dataset.size());
        assertEquals(4, dataset.attributeCount());
        assertEquals(3, dataset.getClasses().size());

    }

}
