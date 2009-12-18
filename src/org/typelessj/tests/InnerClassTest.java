//
// $Id$

package org.typelessj.tests;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

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
                // trigger isStaticReceiver processing
                return Integer.valueOf(String.valueOf(value)) * 5;
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

    @Test public void testAnonLibraryInst () {
        int size = 25;
        Map<Integer,Integer> map = new HashMap<Integer,Integer>(size) {
        };
        map.put(5, 10);
        assertEquals(map.get(5), 10);
    }

    // test inner class handling with no enclosing member
    protected Value _value = new Value() {
        public int value () {
            return 3;
        }
        public int op (int value) {
            return value+1;
        }
    };
}
