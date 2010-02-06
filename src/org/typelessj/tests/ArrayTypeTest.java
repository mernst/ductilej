//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests array related type resolution.
 */
public class ArrayTypeTest
{
    @Test public void testResolveArrayLengthType ()
    {
        char[] foo = "one".toCharArray();
        assertEquals(1, noop(foo.length));
    }

    @Test public void testResolveArrayType ()
    {
        assertEquals(2, noop(new Integer[] { 1, 2, 3 }));
    }

    protected static void noop (Object value) {
        // noop!
    }

    protected static int noop (int value) {
        return 1;
    }

    protected static int noop (Integer[] values) {
        return 2;
    }
}
