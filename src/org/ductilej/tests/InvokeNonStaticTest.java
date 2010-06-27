//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the detection and invocation of non-static methods.
 */
public class InvokeNonStaticTest
{
    public class Foo
    {
        public int triple (int value) {
            return value * 3;
        }

        public int something (int value) {
            return triple(value);
        }

        public String noop (String value) {
            return value;
        }
    }

    public Foo bar = new Foo();

    @Test public void testInvokeNonStatic ()
    {
        Foo test = new Foo();
        int value = test.triple(25);
        assertTrue(value == 75);
        int val2 = bar.triple(3);
        assertTrue(val2 == 9);
        assertEquals(9, test.something(3));

        Foo bar = new Foo() {
            @Override public int something (int value) {
                return 2 * triple(value);
            }
        };
        assertEquals(18, bar.something(3));

        // make sure we generate: RT.invoke(foo, "noop", (Object)null)
        assertTrue(test.noop(null) == null);
    }

    @Test public void testStringLitReceiver ()
    {
        assertEquals("foo".length(), 3);
    }
}
