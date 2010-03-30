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
    }

    @Test public void testNewB ()
    {
        C c = new C();
        assertTrue(c.newB() != null);
    }
}
