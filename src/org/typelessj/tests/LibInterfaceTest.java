//
// $Id$

package org.typelessj.tests;

import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests preservation of library interface implementation signature, in this case compareTo().
 */
public class LibInterfaceTest
{
    public static class Value implements Comparable<Value>
    {
        public int value;

        public Value (int value) {
            this.value = value;
        }

        public int compareTo (Value o) {
            return (value == o.value) ? 0 : ((value > o.value) ? 1 : -1);
        }
    }

    @Test public void testLibInterface ()
    {
        Value[] values = new Value[] { new Value(3), new Value(1), new Value(99) };
        Arrays.sort(values);
        assertTrue(values[0].value == 1);
        assertTrue(values[1].value == 3);
        assertTrue(values[2].value == 99);
    }
}
