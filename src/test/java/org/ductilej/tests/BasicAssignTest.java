//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests various assignment transformations.
 */
public class BasicAssignTest
{
    // make sure the type of this initializer expression gets cast back to the declared type as we
    // don't detype static final fields
    public static final Boolean debug = Boolean.getBoolean("debug");

    public class MutableInt {
        public int value;
        public MutableInt (int value) {
            this.value = value;
        }
    }

    @Test public void assignTest ()
    {
        int ivalue = 42;
        assertTrue(ivalue == 42);
        String svalue = "hello";
        assertEquals(svalue, "hello");
        int[] iarray = new int[1];
        iarray[0] = 42;
        assertEquals(iarray[0], 42);
        MutableInt iobj = new MutableInt(42);
        iobj.value = -24;
        assertEquals(iobj.value, -24);
    }

    @Test public void testDefiniteAssign ()
    {
        String a = "Hello!", b;
        boolean cond = true;
        if (cond && (b = next()) != null) {
            a = b;
        }
        assertEquals("Hello!", a);
    }

    protected static String next() {
        return null;
    }
}
