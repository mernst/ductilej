//
// $Id$

package org.typelessj.example;

/**
 * An example app on which we demonstrate the untyper.
 */
public class ExampleApp
{
    public static class Tester
    {
        public int compute (int value) {
            return value * 2 + 5;
        }
    }

    public static void main (String[] args)
    {
        Tester tester = new Tester();

        int age = 25;
        Object value = tester.compute(age);
        System.out.println("Value " + value);

        String name = "Phineas P. Gage";
        String text = append(name);
        System.out.println("Text " + text);
    }

    protected static String append (String value)
    {
        return value + " was.";
    }
}
