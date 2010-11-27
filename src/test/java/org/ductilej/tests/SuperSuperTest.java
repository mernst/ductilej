//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests detecting as library method a method overridden in a non-library super class.
 */
public class SuperSuperTest
{
    public static class A implements Comparable<A>
    {
        public final String value;
        public A (String value) {
            this.value = value;
        }

        public int compareTo (A other) {
            return value.compareTo(other.value);
        }

        public int foo (A other) {
            return value.compareTo(other.value);
        }

        public String toString () {
            return value;
        }
    }

    public static class B extends A
    {
        public B (String value) {
            super(value);
        }

        @Override public int compareTo (A other) {
            return super.compareTo(other);
        }

        @Override public int foo (A other) {
            return super.foo(other);
        }

        @Override public String toString () {
            return "b:" + value;
        }
    }

    @Test public void testSuperSuper ()
    {
        B b1 = new B("one");
        B b2 = new B("two");
        assertTrue(b1.compareTo(b2) < 0);
    }
}
