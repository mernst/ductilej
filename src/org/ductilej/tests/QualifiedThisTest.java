//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

public class QualifiedThisTest
{
    public static class A {
        public String foo () {
            return "foo";
        }
    }

    public static class B extends A {
        public class C {
            public String bar () {
                return foo(); // --> RT.invoke("foo", B.this); not A.this
            }
        }
    }

    @Test public void testQualifiedThis ()
    {
        B b = new B();
        B.C c = b.new C();
        assertEquals("foo", c.bar());
    }
}
