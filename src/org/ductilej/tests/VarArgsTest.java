//
// $Id$

package org.ductilej.tests;

import java.util.Date;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests varargs method selection.
 */
public class VarArgsTest
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
// TEMP: broken until runtime overload resolution improves
//         assertEquals(foo("one", "two", "three"), "foo(String,String...)");
//         assertEquals(foo("one", 2, 3), "foo(String,Integer...)");
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

    @Test public void testNullDisambig ()
    {
        assertEquals(-1, varargs((Object[])null));
        assertEquals(0, varargs());
        // TODO: we can't handle this yet until we communicate the static type of the final null to
        // the runtime somehow...
        // assertEquals(1, varargs((Object)null));
        assertEquals(1, varargs(1));
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

    protected static int varargs (Object... args)
    {
        return (args == null) ? -1 : args.length;
    }
}
