//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests edge cases related to primitive default values.
 */
public class DefPrimValTest
{
    public static abstract class Parent {
        public Parent () {
            initField();
        }
        protected abstract void initField ();
    }

    public static class Child extends Parent {
        public int getField () {
            return _field;
        }
        protected void initField () {
            _field = 10;
        }
        private int _field;
    }

    @Test public void testInitField () {
        Child c = new Child();
        assertEquals(10, c.getField());
    }
}
