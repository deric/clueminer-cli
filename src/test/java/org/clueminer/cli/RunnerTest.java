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
 * @param <E>
 */
public class RunnerTest<E extends Instance> {

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
        Dataset<E> dataset = subject.loadData(p);
        assertEquals(150, dataset.size());
        assertEquals(4, dataset.attributeCount());
        assertEquals(3, dataset.getClasses().size());
    }

    @Test
    public void testParseFile() throws Exception {
        p.clsIndex = 4;
        p.header = false;
        p.type = "txt";
        Dataset<E> dataset = subject.loadData(p);
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
        Dataset<E> dataset = subject.loadData(p);
        assertEquals(150, dataset.size());
        assertEquals(4, dataset.attributeCount());
        assertEquals(3, dataset.getClasses().size());
    }

}
