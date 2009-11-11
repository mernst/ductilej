//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

import org.typelessj.tests.StaticHelper;

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
        assertEquals(value, 12);

        // a static method defined on an implicit Java class
        Integer foo = Integer.valueOf(25);
        assertEquals(foo, (Integer)25); // this cast needed for normal compilation

        // a static method defined on this class
        value = triple(42);
        assertEquals(value, 126);

        // a static method of an inner class calling one in neighboring scope
        value = MoreTester.threeHalves(42);
        assertEquals(value, 63);

        // a static method of an inner class calling one from broader scope
        value = InvokeStaticTest.Tester.tripleDouble(42);
        assertEquals(value, 252);

        // test finding a qualified inner inner class
        value = MoreTester.InnerTester.oneFourth(42);
        assertEquals(value, 10);

        // test a static method of another class in this package
        value = StaticHelper.help(1);
        assertEquals(value, 43);

        // test a static method of another class in this package
        value = StaticHelper.Inner.innerHelp(1);
        assertEquals(value, 25);

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
                return value;
            }
        }
    }
}
