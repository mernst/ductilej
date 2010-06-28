//
// $Id$

package org.ductilej.tests;

import java.util.Date;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests method selection of overloaded varargs methods.
 */
public class OverVarArgsTest
{
    public class Varrgh {
        public final String name;
        public final int[] values;

        public Varrgh (String name, int... values) {
            this.name = name;
            this.values = values;
        }

        public Varrgh (int number, int... values) {
            this(String.valueOf(number), values);
        }

        public Varrgh (Date one, int two) {
            this(one.toString(), two);
        }

        public Varrgh (boolean one) {
            this(String.valueOf(one));
        }
    }

    @Test public void testRuntimeSelect ()
    {
        assertEquals(foo("one", "two", "three"), "foo(String,String...)");
        assertEquals(foo("one", 2, 3), "foo(String,Integer...)");
    }

    @Test public void testVarArgs ()
    {
        // make sure none of the following throw MethodNotFoundError
        log("one");
        log("one", (Object)new String[] { "two", "three" });
        log("one", "two", "three");
    }

    @Test public void testGroupVarArgs ()
    {
        Varrgh v = new Varrgh(true);
        assertEquals("true", v.name);
    }

    protected static String foo (String one, String... two)
    {
        return "foo(String,String...)";
    }

    protected static String foo (String one, Integer... two)
    {
        return "foo(String,Integer...)";
    }

    protected static void log (String message, Object... args)
    {
        // System.out.println(message + " " + args);
    }
}
