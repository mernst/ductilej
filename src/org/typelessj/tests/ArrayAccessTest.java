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
}
