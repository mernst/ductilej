//
// $Id$

package org.ductilej.tests;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests special handling for JUnit 3 test cases.
 */
public class OldJUnitTest extends TestCase
{
    public static TestSuite suite () {
        return new TestSuite(OldJUnitTest.class);
    }

    // we need to make sure that this ctor is not detyped
    public OldJUnitTest (String name) {
        super(name);
    }

    // if this method gets called at all, things are working
    public void testTest () {
        assertEquals(1+1, 2);
    }
}
