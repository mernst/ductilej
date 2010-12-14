//
// $Id$

package org.ductilej.dtests;

import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests that we can pass non-matching arguments to constructor.
 */
public class ConstructorArgsTest
{
    static class Foo {
        public Foo(String arg0, String arg1, String arg2, Date arg3) {
        }
    }

    @Test(expected=NoSuchMethodError.class)
    public void testNonmatchingArgs() {
        Foo f = new Foo("", "", "", "");
    }
}

