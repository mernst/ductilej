//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests select transformation and non-transformation.
 */
public class SelectTest
{
    @Test public void testSelect ()
    {
        Value five = new Value(5);
        assertTrue(five.value == 5);
        assertTrue(toValue(42).value == 42);
        assertTrue(new Value(180).value == 180);
    }

    protected Value toValue (int value)
    {
        return new Value(value);
    }

    protected static class Value
    {
        public int value;
        public Value (int value) {
            this.value = value;
        }
    }
}
