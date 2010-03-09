//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests array access.
 */
public class ArrayAccessTest
{
    @Test public void testArrayAccess ()
    {
        String[] args = new String[] { "one", "two", "three" };
        String text = "";
        for (String arg : args) {
            text = text + arg;
        }
        assertEquals(text, "onetwothree");
        text = "";
        for (int ii = 0; ii < args.length; ii++) {
            text = text + args[ii];
        }
        assertEquals(text, "onetwothree");
    }

    @Test public void testArrayIndexedMethodCall ()
    {
        String[] args = new String[] { "one", "two", "three" };
        int length = 0;
        for (int ii = 0; ii < args.length; ii++) {
            length += args[ii].length();
        }
        assertEquals(length, 11);
    }
}
