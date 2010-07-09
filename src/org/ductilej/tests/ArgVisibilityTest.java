//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of methods with inaccessible argument types.
 */
public class ArgVisibilityTest
{
    public static class B {
        public int callFoo (A a) {
            return a.foo(null);
        }
    }

    @Test public void testCall () {
        B b = new B();
        assertEquals(1, b.callFoo(new A()));
    }
}

class A {
    public int foo (Private p) {
        return 1;
    }
    private static class Private {
    }
}
