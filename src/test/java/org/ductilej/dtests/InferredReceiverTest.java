//
// $Id$

package org.ductilej.dtests;

import org.junit.Test;
import org.junit.Assert;
import static org.junit.Assert.*;

/**
 * Tests various circumstances in which we must use heuristics to determine whether a receiver is
 * static.
 */
public class InferredReceiverTest
{
    public static class Tester {
        public static String staticTest (String arg) {
            return "static:" + arg;
        }

        public String test (String arg) {
            return "non-static:" + arg;
        }
    }

    @Test public void testSimpleStatic () {
        assertEquals("static:foo", Tester.staticTest("foo"));
    }

    @Test public void testSimpleNonStatic () {
        Object t = new Tester();
        // TODO: we fail to resolve assertEquals() because t.test cannot be resolved
        assertEquals("non-static:foo", t.test("foo"));
    }

    @Test public void testArrayReceiver () {
        Object[] ts = { new Tester() };
        // TODO: we fail to resolve assertEquals() because ts[0].test cannot be resolved
        assertEquals("non-static:foo", ts[0].test("foo"));
    }
}
