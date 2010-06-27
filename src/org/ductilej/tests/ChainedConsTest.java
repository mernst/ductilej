//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the handling of chained constructors.
 */
public class ChainedConsTest
{
    public static class A {
        public final int foo;

        public A (int foo) {
            this.foo = foo;
        }

        public A (String foo) {
            this.foo = Integer.valueOf(foo);
        }
    }

    public static class B extends A {
        public B (int foo, int bar) {
            super(foo);
        }
    }

    public static class C extends A {
        public C (int foo) {
            super(foo);
        }

        public C (String foo) {
            super(foo);
        }

        public C (int foo, int bar) {
            this(""+foo);
        }
    }

    @Test public void testChainedCons ()
    {
        A ab = new B(42, 42);
        assertEquals(ab.foo, 42);

        A ac1 = new C(42);
        assertEquals(ac1.foo, 42);

        A ac2 = new C(42, 42);
        assertEquals(ac2.foo, 42);
    }
}
