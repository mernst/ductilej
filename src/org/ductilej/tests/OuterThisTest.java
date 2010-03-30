//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests resolution of which outer-this to use in a constructor.
 */
public class OuterThisTest
{
    public class B {
    }

    public class C {
        public B newB () {
            return new B();
        }
        public D newD () {
            return new D();
        }
    }

    public static class D {
    }

    @Test public void testThisResolve ()
    {
        C c = new C();
        assertTrue(c.newB() != null);
    }

    @Test public void testNoThis ()
    {
        C c = new C();
        assertTrue(c.newD() != null);
    }
}
