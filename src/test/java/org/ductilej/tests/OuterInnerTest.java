//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

public class OuterInnerTest
{
    public class Outer {
        public class Inner {
            public final int value;
            public Inner (int value) {
                this.value = value;
            }
        }
    }

    @Test public void test () {
        Outer o = new Outer();
        Outer.Inner i = o.new Inner(10);
        assertEquals(10, i.value);
    }
}
