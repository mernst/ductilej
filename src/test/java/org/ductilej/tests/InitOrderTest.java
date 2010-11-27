//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Points out an edge case with finals that we can't help.
 */
public class InitOrderTest
{
    public static abstract class A {
        public int fooVal = foo();
        protected abstract int foo ();
    }

    public static class B extends A {
        public final int bar;
        public B () {
            bar = 5;
        }
        protected int foo () {
            return bar;
        }
    }

    @Test public void testInitOrder() {
        B b = new B();
        // assertEquals(0, b.fooVal); // will be 'null' in detyped code
        assertEquals(5, b.bar);
    }
}
