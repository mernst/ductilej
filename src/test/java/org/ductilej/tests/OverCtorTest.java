//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of overloaded constructors.
 */
public class OverCtorTest
{
    public static class Foo {
        public final String value;

        public Foo (Integer value) {
            this.value = String.valueOf(value);
        }

        // in cases of ambiguity, RT.newInstance currently chooses the last matching ctor, which in
        // this case will be the wrong one
        public Foo (String value) {
            this.value = value;
        }
    }

    @Test public void testOverCtor () {
        // at runtime, we won't know which constructor was desired
        Foo foo = new Foo((Integer)null);
        assertEquals("null", foo.value);
    }

    public static class A {
        public final String data;

        public A (long arg) { // detypes to A(Object,long)
            this.data = String.valueOf(arg);
        }
        public A (Object arg) { // detypes to A(Object,Object)
            this.data = String.valueOf(arg);
        }
    }

    public static class B extends A {
        public B () {
            super(0L); // detypes to super(0L,0L)

            // prior to detyping, A(long) matches during the subtyping phase and A(Object) matches
            // only during the method invocation conversion phase (which is never entered because a
            // match existed during the subtyping phase)

            // after detyping A(Object,long) and A(Object,Object) both match during the method
            // invocation conversion phase, but there's no subtype relationship between long and
            // Object, so neither is more specific and they are thus ambiguous
        }
    }

    @Test public void testAmbiguousSuperCons () {
        B b = new B();
        assertEquals("0", b.data);
    }
}
