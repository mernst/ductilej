//
// $Id$

package org.ductilej.tests;

import org.ductilej.tests.helper.SelectSuperHelper;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests configuring the selectSuper bit in AttrContext.
 */
public class SelectSuperTest
{
    public class B extends SelectSuperHelper.A {
        public B (String foo) {
            super(foo);
        }

        public String get () {
            return super.getFoo();
        }
    }

    @Test public void testSelectSuper ()
    {
        B b = new B("Foo");
        assertEquals("Foo", b.get());
    }
}
