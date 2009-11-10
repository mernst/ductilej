//
// $Id$

package org.typelessj.tests;

import java.util.Arrays;
import java.util.Comparator;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests anonymous inner-class handling.
 */
public class InnerClassTest
{
    public interface Value {
        int value ();
        int op (int value);
    }

    @Test public void testDetypedInterface () {
        Value v = new Value() {
            public int value () {
                return 5;
            }
            public int op (int value) {
                return value * 5;
            }
        };
        assertEquals(v.op(v.value()), 25);
    }

    @Test public void testNonDetypedInterface () {
        Integer[] values = new Integer[] { 9, 8, 7, 6, 5, 4, 3, 2, 1 };
        Arrays.sort(values, new Comparator<Integer>() {
            public int compare (Integer a, Integer b) {
                return (a == b) ? 0 : ((a < b) ? -1 : 1);
            }
        });
        assertEquals(values[0], (Integer)1);
        assertEquals(values[2], (Integer)3);
    }
}
