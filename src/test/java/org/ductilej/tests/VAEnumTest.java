//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests special handling for enums with varargs constructors.
 */
public class VAEnumTest
{
    public static enum Letter {
        A, B, C("one", "two"), D("three");

        public String getArgs () {
            return _args;
        }

        Letter (String... args) {
            for (String arg : args) {
                _args += arg;
            }
        }
        protected String _args = "";
    }

    @Test public void testVAE () {
        assertEquals("", Letter.A.getArgs());
        assertEquals("", Letter.B.getArgs());
        assertEquals("onetwo", Letter.C.getArgs());
        assertEquals("three", Letter.D.getArgs());
    }
}
