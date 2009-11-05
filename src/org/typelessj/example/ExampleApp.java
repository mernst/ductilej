//
// $Id$

package org.typelessj.example;

import java.io.IOException;
import java.util.Collections;

/**
 * An example app on which we demonstrate the detyper.
 */
public class ExampleApp
{
    public static interface ITest
    {
        public int testAdd (int value);
    }

    public static class Tester
        implements ExampleApp.ITest
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

        @Override
        public String toString () {
            return "Tester";
        }

        @Override
        public int hashCode () {
            return _multiplier ^ 42;
        }

        public static int computeStatic (int value) {
            return value * 3 + 2;
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
            return 0;
        }
    }

    public static void main (String[] args)
    {
        Tester tester = new Tester("", 2);

        int age = tester.five;
        int value = tester.compute(age);
        System.out.println("Value " + value);

        if (value > 25) {
            value = Tester.computeStatic(age);
            System.out.println("Static value " + value);
        }

        String name = "Phineas P. Gage";
        String text = append(name);
        System.out.println("Text " + text);

//        testArrayAccess(args);
        testArrayCreate();
    }

    protected static String append (String value)
    {
        return value + " was.";
    }

    protected static void testArrayAccess (String[] args)
    {
        for (String arg : args) {
            System.out.println("Arg " + arg);
        }

        for (int ii = 0; ii < args.length; ii++) {
            System.out.println("Arg " + args[ii]);
        }
    }

    protected static void testArrayCreate ()
    {
        String[] test1 = new String[] { "zero", "one", "two" };
        System.out.println("One: " + test1[0] + " " + test1[1] + " " + test1[2]);
        String[] test2 = new String[3];
        System.out.println("Two: " + test2[0] + " " + test2[1] + " " + test2[2]);
        test2[0] = "0";
        test2[1] = "1";
        test2[2] = "2";
        System.out.println("New two: " + test2[0] + " " + test2[1] + " " + test2[2]);
    }

    protected static void testThrows () throws IOException
    {
        throw new IOException("Oh noez!");
    }

//     protected static void fail ()
//     {
//         throw new RuntimeException("Fail!");
//     }
}
