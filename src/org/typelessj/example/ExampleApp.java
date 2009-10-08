//
// $Id$

package org.typelessj.example;

/**
 * An example app on which we demonstrate the detyper.
 */
public class ExampleApp
{
    public static class Tester
    {
        public int compute (int value) {
            return value * 2 + 5;
        }

        @Override
        public String toString () {
            return "Tester";
        }

        public static int computeStatic (int value) {
            return value * 3 + 2;
        }
    }

    public static class MoreTester extends Tester
        implements Comparable<MoreTester>
    {
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
        Tester tester = new Tester();

        int age = 25;
        int value = tester.compute(age);
        System.out.println("Value " + value);

        value = Tester.computeStatic(age);
        System.out.println("Static value " + value);

        String name = "Phineas P. Gage";
        String text = append(name);
        System.out.println("Text " + text);
    }

    protected static String append (String value)
    {
        return value + " was.";
    }
}
