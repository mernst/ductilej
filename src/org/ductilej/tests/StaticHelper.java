//
// $Id$

package org.ductilej.tests;

/**
 * Used to test locating classes in one's same package.
 */
public class StaticHelper
{
    public static final int HELPFUL = 42;

    public static class Inner {
        public static int innerHelp (int value) {
            return value + 24;
        }

        public int triple (int value) {
            return value * 3;
        }
    }

    public static final Inner inner = new Inner();

    public static int help (int value)
    {
        return value + 42;
    }
}
