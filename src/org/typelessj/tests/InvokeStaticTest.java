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
    public static class Tester
    {
        public static int half (int value) {
            return value / 2;
        }

        public static int tripleDouble (int value) {
            return triple(value) * 2;
        }
    }

    public static class MoreTester
    {
        public static int threeHalves (int value) {
            return Tester.half(3 * value);
        }
    }

    @Test public void testInterfaces ()
    {
        // a static method defined on an inner class
        int value = Tester.half(25);
        System.out.println("Static value " + value);

        // a static method defined on an implicit Java class
        Integer foo = Integer.valueOf(25);
        System.out.println("Int value " + foo);

        // a static method defined on this class
        value = triple(42);
        System.out.println("Tripled value " + value);

        // a static method of an inner class calling one in neighboring scope
        value = MoreTester.threeHalves(42);
        System.out.println("Three-halved value " + value);

        // a static method of an inner class calling one from broader scope
        value = tripleDouble(42);
        System.out.println("Triple-doubled value " + value);
    }

    protected static int triple (int value)
    {
        return value * 3;
    }
}
