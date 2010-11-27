//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

import org.ductilej.tests.helper.ProtectedHelper;

/**
 * Tests proper resolution of protected constructor when used by an anonymous inner class.
 */
public class ProtectedConstructorTest
{
    @Test public void testPC () {
        ProtectedHelper a = new ProtectedHelper("test") {
        };
        assertEquals("test", a.value);
    }
}
