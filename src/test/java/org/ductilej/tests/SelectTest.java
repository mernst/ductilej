//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests select transformation and non-transformation.
 */
public class SelectTest
{
    public static final Value twofour = new Value(24);

    @Test public void testSelect ()
    {
        Value five = new Value(5);
        assertTrue(five.value == 5);
        assertTrue(toValue(19).value == 19);
        assertTrue(five.someText.length() == 10);
        assertTrue(five.moreText().length() == 10);
        // SelectTest.Value shouldn't be transformed, expr.value should
        assertTrue(new SelectTest.Value(180).value == 180);
        // since SelectTest.Value is a class name, expr.theAnswer is not xformed
        assertTrue(SelectTest.Value.theAnswer == 42);
        // SelectTest.twofour should not be transformed, expr.value should
        assertTrue(SelectTest.twofour.value == 24);
    }

    protected Value toValue (int value)
    {
        return new Value(value);
    }

    protected static class Value
    {
        public static int theAnswer = 42;

        public int value;

        public String someText = "Some text!";

        public Value (int value) {
            this.value = value;
        }

        public String moreText () {
            return "More text!";
        }
    }
}
