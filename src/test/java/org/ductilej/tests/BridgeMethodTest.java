//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests our handling of situations which normally require bridge methods.
 */
public class BridgeMethodTest
{
    public static interface Function<A,R> {
        public R apply (A arg);
    }

    public static <A, R> R applyFunc (Function<A,R> func, A arg) {
        return func.apply(arg);
    }

    @Test public void testBridgedApply () {
        Function<String, String> lower = new Function<String, String>() {
            public String apply (String arg) {
                return arg.toLowerCase();
            }
        };
        assertEquals("hello", applyFunc(lower, "Hello"));
    }
}
