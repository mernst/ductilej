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
}
