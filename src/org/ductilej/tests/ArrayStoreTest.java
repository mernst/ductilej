//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests array store.
 */
public class ArrayStoreTest
{
    @Test public void testArrayStore ()
    {
        String[] test1 = new String[] { "zero", "one", "two" };
        assertEquals(test1[0] + " " + test1[1] + " " + test1[2], "zero one two");
        String[] test2 = new String[3];
        assertTrue(test2[0] == null);
        assertTrue(test2[1] == null);
        assertTrue(test2[2] == null);
        test2[0] = "0";
        test2[1] = "1";
        test2[2] = "2";
        assertEquals(test2[0], "0");
        assertEquals(test2[1], "1");
        assertEquals(test2[2], "2");
    }
}
