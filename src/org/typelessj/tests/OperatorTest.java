//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests operator transformation.
 */
public class OperatorTest
{
    @Test public void testUnary ()
    {
        int ii = 1;
        assertEquals(ii++, 1);
        assertEquals(++ii, 3);
        assertEquals(ii--, 3);
        assertEquals(--ii, 1);

        int[] values = new int[1];
        assertEquals(values[0]++, 0);
        assertEquals(++values[0], 2);
        assertEquals(values[0]--, 2);
        assertEquals(--values[0], 0);

        Value value = new Value();
        assertEquals(value.value++, 0);
        assertEquals(++value.value, 2);
        assertEquals(value.value--, 2);
        assertEquals(--value.value, 0);
    }

    @Test public void testAssignOp ()
    {
        int ii = 1;
        ii += 1;
        assertEquals(ii, 2);
        ii -= 1;
        assertEquals(ii, 1);

        int[] values = new int[1];
        values[0] += 1;
        assertEquals(values[0], 1);

        Value value = new Value();
        value.value += 1;
        assertEquals(value.value, 1);
    }

    protected static class Value {
        public int value = 0;
    }
}
