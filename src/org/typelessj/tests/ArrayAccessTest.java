//
// $Id$

package org.typelessj.tests;

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

    @Test public void testResolveArrayLengthType ()
    {
        char[] foo = "one".toCharArray();
        assertEquals(ident(foo.length), 3);
    }

    // used to test resolving type of array.length
    protected static int ident (int value) {
        return value;
    }
}
