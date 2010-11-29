//
// $Id$

package org.ductilej.dtests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the fallback method resolution code which must determine whether to call a static or
 * non-static method with invalid arguments.
 */
public class FindStaticMethodTest
{
    public interface Database {
    }

    public static class TestDatabase {
        public String get (String key) {
            return key + "!";
        }
    }

    public static String dbGet (Database db, String key)
    {
        return db.get(key);
    }

    public static void callStaticTests ()
    {
        // we're in a static context here, so Ductile must be sure not to emit a non-static call
        String rv = dbGet(new TestDatabase(), "test");
        assertEquals("test!", rv);
        // TODO: the following breaks because dbGet() fails to resolve, and thus we fail to resolve
        // the correct assertEquals()
        // assertEquals("test!", dbGet(new TestDatabase(), "test"));
    }

    @Test public void testStaticFinding ()
    {
        callStaticTests();
    }
}
