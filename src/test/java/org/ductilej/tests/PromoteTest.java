//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests fiddly bits related to primitive type promotion.
 */
public class PromoteTest
{
    @Test public void testNarrow ()
    {
        short value = 1; // int literal narrowed to short
        assertEquals((short)1, value);

        value = FIVE;
        assertEquals((short)5, value);
    }

    @Test public void testShortPromote ()
    {
        short val1 = 1, val2 = 2;
        Object val = val1 + val2; // + result is numeric promoted to int
        assertEquals(Integer.class, val.getClass());
    }

    @Test public void testLongNonPromote ()
    {
        long val1 = 1, val2 = 2;
        Object val = val1 + val2; // + result remains long
        assertEquals(Long.class, val.getClass());
    }

    @Test public void testTypingAndRuntimeNonPromote ()
    {
        // ensure that during detyping the type of _nextInternCode++ resolves to short not int, and
        // ensures that at runtime we also don't inadvertently promote the short to an int
        short value = box(_counter++);
        assertEquals(1, value);
    }

    protected Short box (short code)
    {
        return code;
    }

    protected short _counter = 1;
    protected static final int FIVE = 5;
}
