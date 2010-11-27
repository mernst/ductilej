//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests transformation and non-transformation (as appropriate) of interface implementing methods.
 */
public class InterfaceTest
{
    public static interface ITest
    {
        int testAdd (int value);

        // fields declared in interfaces are implicitly "public static final"
        int ONE = 1;
        int TWO = ONE+1;
        int FOUR = (ONE+1)*2;
        int MAX = Integer.MAX_VALUE;

        // test constant expression detection's handling of a cast
        byte BYTE_TEST = (byte) 80;
    }

    public static class Tester
        // using fully qualified name here to ensure we don't transform this select
        implements InterfaceTest.ITest
    {
        public int five = 5;

        public Tester (String unused, int multiplier) {
            _multiplier = multiplier;
        }

        public int compute (int value) {
            return value * _multiplier + 5;
        }

        public int testSwitch (int value) {
            // tests that the constants declared in ITest were properly preserved
            switch (value) {
            case ONE: return -1;
            case TWO: return -2;
            case FOUR: return -4;
            case MAX: return 0;
            case BYTE_TEST: return 1;
            default: return value;
            }
        }

        // from interface ITest
        public int testAdd (int value) {
            return value + 25;
        }

        protected int _multiplier;
    }

    public static class MoreTester extends Tester
        implements Comparable<MoreTester>
    {
        public MoreTester (int multiplier) {
            super("", multiplier);
        }

        @Override public int compute (int value) {
            return value * 2 + 3;
        }

        // from interface Comparable<MoreTester>
        public int compareTo (MoreTester o) {
            return (_multiplier == o._multiplier) ? 0 : ((_multiplier > o._multiplier) ? 1 : -1);
        }
    }

    @Test public void testInterfaces ()
    {
        Tester tester = new Tester("", 2);
        int age = tester.five;
        int value = tester.compute(age);
        assertEquals(15, value);
        assertEquals(-1, tester.testSwitch(1));
    }
}
