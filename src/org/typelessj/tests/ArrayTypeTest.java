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
        assertEquals(noop(foo.length), 1);
    }

    @Test public void testResolveArrayType ()
    {
        assertEquals(noop(new Integer[] { 1, 2, 3}), 2);
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
