//
// $Id$

package org.ductilej.tests;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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

    @Test public void testNullDisambig ()
    {
        assertEquals(-1, varargs((Object[])null));
        assertEquals(0, varargs());
        assertEquals(1, varargs(1));
        // this should get wrapped into a one element array containing null
        assertEquals(1, varargs((Object)null));
    }

    @Test public void testVarArgsArrayType ()
    {
        List<Integer> list = Arrays.asList((Integer) null);
        Object[] array = list.toArray();
        assertEquals(array.getClass(), Integer[].class);
    }

    @Test public void testLibraryNullVarArray ()
        throws NoSuchMethodException
    {
        Constructor<?> ctor = VarArgsTest.class.getDeclaredConstructor((Class<?>[]) null);
        assertTrue(ctor != null);
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
