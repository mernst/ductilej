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

    public class InnerA {
        public final String arg;
        public InnerA (String arg) {
            this.arg = arg;
        }
    }

    public class InnerB extends InnerA {
        public InnerB () {
            super(getOuterString());
        }
    }

    public class ParentOuter {
        public class Inner1 {
        }
    }

    public class ChildOuter extends ParentOuter {
        public class Inner2 {
            public Inner1 newOne () {
                // this needs to xform to: RT.newInstance(Inner1.class, ChildOuter.this) not
                // RT.newInstance(Inner1.class, ParentOuter.this) which would naturally happen if
                // we just used the owner of the to be instantiated inner class
                return new Inner1();
            }
        }
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

    @Test public void testOuterThisInSuper ()
    {
        InnerB b = new InnerB();
        assertEquals("Outer", b.arg);
    }

    @Test public void testParentInner1FromChildInner2 ()
    {
        ChildOuter outer = new ChildOuter();
        ChildOuter.Inner2 inner = outer.new Inner2();
        assertTrue(inner.newOne() != null);
    }

    protected String getOuterString ()
    {
        return "Outer";
    }
}
