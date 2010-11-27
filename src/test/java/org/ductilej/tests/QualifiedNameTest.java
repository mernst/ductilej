//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests resolution of fully-qualified class names.
 */
public class QualifiedNameTest
{
    public static boolean test = false;

    @Test public void testConstant ()
    {
        assertEquals("Center", java.awt.BorderLayout.CENTER);
    }

    @Test public void testClassLiteral ()
    {
        assertEquals("[Ljava.lang.String;", String[].class.getName());
    }

    @Test public void testSameClassStaticField ()
    {
        QualifiedNameTest.test = true;
        assertEquals(true, QualifiedNameTest.test);
    }
}
