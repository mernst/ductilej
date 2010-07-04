//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests various constructor related bits.
 */
public class CtorTest
{
    public class Wrapper {
        public final String value;
        public Wrapper (String value) {
            this.value = value;
        }
    }

    public class VarWrapper {
        public final String[] values;
        public VarWrapper (String... values) {
            this.values = values;
        }
    }

    @Test public void testNullArg () {
        Wrapper w = new Wrapper(null);
        assertTrue(null == w.value);

        VarWrapper vw = new VarWrapper((String[])null);
        assertTrue(null == vw.values);

        vw = new VarWrapper((String)null);
        assertTrue(1 == vw.values.length);
    }
}
