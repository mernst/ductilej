//
// $Id$

package org.ductilej.dtests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests that we can compile code with a missing method and that we throw a NoSuchMethodError at
 * runtime.
 */
public class MissingMethodTest
{
    static class Bar {
    }

    static class Foo {
        public Bar firstMethod() { return new Bar(); }
    }

    @Test(expected=NoSuchMethodError.class)
    public void testUndefinedMethod() {
        Foo f = new Foo();
        f.firstMethod().secondMethod();
    }
}
