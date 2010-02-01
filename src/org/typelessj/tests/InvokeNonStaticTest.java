//
// $Id$

package org.typelessj.tests;

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
    }

    public Foo bar = new Foo();

    @Test public void testInvokeNonStatic ()
    {
        Foo test = new Foo();
        int value = test.triple(25);
        assertTrue(value == 75);
        int val2 = bar.triple(3);
        assertTrue(val2 == 9);
    }

    @Test public void testStringLitReceiver ()
    {
        assertEquals("foo".length(), 3);
    }
}
