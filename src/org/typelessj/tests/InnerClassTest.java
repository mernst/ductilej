//
// $Id$

package org.typelessj.tests;

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

    @Test public void testAnonInterface () {
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
}
