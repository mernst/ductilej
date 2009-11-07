//
// $Id$

package org.typelessj.tests;

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
        assertEquals(value, 12);

        // a static method defined on an implicit Java class
        Integer foo = Integer.valueOf(25);
        assertEquals(foo, 25);

        // a static method defined on this class
        value = triple(42);
        assertEquals(value, 126);

        // a static method of an inner class calling one in neighboring scope
        value = MoreTester.threeHalves(42);
        assertEquals(value, 63);

        // a static method of an inner class calling one from broader scope
        value = Tester.tripleDouble(42);
        assertEquals(value, 252);

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
    }
}
