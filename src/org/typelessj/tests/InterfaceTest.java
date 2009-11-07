//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests transformation and non-transformation (as appropriate) of interface implementing methods.
 */
public class InterfaceTest
{
    public static interface ITest
    {
        public int testAdd (int value);
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
        assertEquals(value, 15);
    }
}
