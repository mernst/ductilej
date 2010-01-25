//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests varargs method selection.
 */
public class VarArgsTest
{
// TODO: instate when we have overload resolution handled
//     @Test public void testRuntimeSelect ()
//     {
//         assertEquals(foo("one", "two", "three"), "foo(String,Integer...");
//         assertEquals(foo("one", 2, 3), "foo(String,Integer...");
//     }

//     protected static String foo (String one, String... two)
//     {
//         return "foo(String,String...)";
//     }

//     protected static String foo (String one, Integer... two)
//     {
//         return "foo(String,Integer...)";
//     }

    @Test public void testVarArgs ()
    {
        // make sure none of the following throw MethodNotFoundError
        log("one");
        log("one", new String[] { "two", "three" });
        log("one", "two", "three");
    }

    protected static void log (String message, Object... args)
    {
        // System.out.println(message + " " + args);
    }
}
