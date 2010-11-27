//
// $Id$

package org.ductilej.tests;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Callable;

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

        Comparator<Integer> icomp = new Comparator<Integer>() {
            public int compare (Integer one, Integer two) {
                return one.compareTo(two);
            }
        };
        // if I use ClassLiteral where Detype turns this into invokeStatic(), then mysteriously
        // *this* assertion starts failing with "unrecognized symbol org (in the
        // org.junit.Assert.class tree), but the assertTrue transformations *above* the Comparator
        // instantion are fine, and if I comment out Comparator, this one also becomes fine (or
        // rather one that doesn't refer to the comparator is fine); whiskey tango foxtrot?
        assertTrue(icomp.compare(0, 5) < 0);
    }

    @Test public void testOverride ()
    {
        Value v = new Value(15) {
            @Override public String toString() {
                return String.valueOf(value);
            }
        };
        assertEquals("15", v.toString());
    }

    protected static <V> void testForall (final V dummy)
    {
        Comparator<V> icomp = new Comparator<V>() {
            public int compare (V one, V two) {
                return 0;
            }
        };
        Callable<V> c = new Callable<V>() {
            public V call () {
                // V foo = null;
                return null;
            }
        };
    }
}
