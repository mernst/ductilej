//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests various assignment transformations.
 */
public class AssignTest
{
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
}
