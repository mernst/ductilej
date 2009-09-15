//
// $Id$

package org.typelessj.example;

/**
 * An example app on which we demonstrate the untyper.
 */
public class ExampleApp
{
    public static void main (String[] args)
    {
        int age = 25;
        int value = compute(age);
        System.out.println("Value " + value);

        String name = "Phineas P. Gage";
        String text = append(name);
        System.out.println("Text " + text);
    }

    protected static int compute (int value)
    {
        return value * 2 + 5;
    }

    protected static String append (String value)
    {
        return value + " was.";
    }
}
