//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the detection and invocation of static methods.
 */
public class InvokeStaticTest
{
    @Test public void testInvokeStatic ()
    {
        java.util.Calendar cal = java.util.Calendar.getInstance();

        // a static method defined on an inner class
        int value = Tester.half(25);
        assertEquals(12, value);

        // a static method defined on an implicit Java class
        Integer foo = Integer.valueOf(25);
        assertEquals((Integer)25, foo); // this cast needed for normal compilation

        // a static method defined on this class
        value = triple(42);
        assertEquals(126, value);

        // a static method of an inner class calling one in neighboring scope
        value = MoreTester.threeHalves(42);
        assertEquals(63, value);

        // a static method of an inner class calling one from broader scope
        value = InvokeStaticTest.Tester.tripleDouble(42);
        assertEquals(252, value);

        // test finding a qualified inner inner class
        value = MoreTester.InnerTester.oneFourth(42);
        assertEquals(10, value);

        // test a static method of another class in this package
        value = StaticHelper.help(1);
        assertEquals(43, value);

        // test a static method of another class in this package
        value = StaticHelper.Inner.innerHelp(1);
        assertEquals(25, value);

        // TODO: test statically imported method (or just use assertTrue());
    }

    protected static int triple (int value)
    {
        return value * 3;
    }

    protected static class Tester
    {
        public static int half (int value) {
            return value / 2;
        }

        public static int tripleDouble (int value) {
            return triple(value) * 2;
        }
    }

    protected static class MoreTester
    {
        public static int threeHalves (int value) {
            return Tester.half(3 * value);
        }

        protected static class InnerTester {
            public static int oneFourth (int value) {
                return value/4;
            }
        }
    }
}
