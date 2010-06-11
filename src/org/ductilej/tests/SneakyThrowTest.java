//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the sneaky throwing of checked exceptions.
 */
public class SneakyThrowTest
{
    @Test public void testSneakyThrow () throws Exception {
        try {
            SneakyThrower.sneakyThrow();
            fail("No exception thrown");
        } catch (Exception e) {
            if (!(e instanceof SomeCheckedException)) {
                throw e;
            }
        }
    }

    protected static class SneakyThrower {
        public static void sneakyThrow() {
            try {
                SneakyThrower.class.newInstance();
            } catch (IllegalAccessException e) {
            } catch (InstantiationException e) {
            }
        }
        SneakyThrower() throws SomeCheckedException {
            throw new SomeCheckedException();
        }
    }

    protected static class SomeCheckedException extends Exception {
    }
}
