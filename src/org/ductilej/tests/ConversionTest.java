//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests various places where implicit widening conversion must be performed.
 */
public class ConversionTest
{
    @Test public void testInitializerConversion () {
        long value = VALUE;
        assertEquals(RESULT, A + B * value);
    }

    @Test public void testAssignmentConversion () {
        long value = 0L;
        value = VALUE;
        assertEquals(RESULT, A + B * value);
    }

    @Test public void testReturnValueConversion () {
        assertEquals(RESULT, A + B * getValue());
    }

    @Test public void testArgumentConversion () {
        assertEquals(469230040, compute(VALUE));
    }

    protected static long compute (long value) {
        return A + B * value;
    }

    protected static long getValue () {
        return VALUE;
    }

    protected static final long A = 13774830040L;
    protected static final int B = -154;
    protected static final int VALUE = 86400000;
    protected static final int RESULT = 469230040;
}
