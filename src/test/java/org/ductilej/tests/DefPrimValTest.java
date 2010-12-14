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
        // our previous naive approach to ensuring that this field, once detyped, received an
        // appropriate initialization to zero resulted in overwriting the initialization that takes
        // place via the call to initField() by the superclass constructor; our new approach avoids
        // such overwriting
        private int _field;
    }

    public static class InstanceField {
        public boolean falseBoolean;
        public byte zeroByte;
        public short zeroShort;
        public int zeroInt;
        public long zeroLong;
        public float zeroFloat;
        public double zeroDouble;
    }

    public static class StaticField {
        public static boolean falseBoolean;
        public static byte zeroByte;
        public static short zeroShort;
        public static int zeroInt;
        public static long zeroLong;
        public static float zeroFloat;
        public static double zeroDouble;
    }

    @Test public void testAvoidOverwrite () {
        Child c = new Child();
        assertEquals(10, c.getField());
    }

    @Test public void testInstanceInit () {
        InstanceField inst = new InstanceField();
        assertEquals(false, inst.falseBoolean);
        assertEquals((byte)0, inst.zeroByte);
        assertEquals((short)0, inst.zeroShort);
        assertEquals(0, inst.zeroInt);
        assertEquals(0l, inst.zeroLong);
        assertEquals(0f, inst.zeroFloat, 0f);
        assertEquals(0d, inst.zeroDouble, 0d);
    }

    @Test public void testStaticInit () {
        assertEquals(false, StaticField.falseBoolean);
        assertEquals((byte)0, StaticField.zeroByte);
        assertEquals((short)0, StaticField.zeroShort);
        assertEquals(0, StaticField.zeroInt);
        assertEquals(0l, StaticField.zeroLong);
        assertEquals(0f, StaticField.zeroFloat, 0f);
        assertEquals(0d, StaticField.zeroDouble, 0d);
    }
}
