//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

import static org.typelessj.tests.StaticHelper.inner;
import static org.typelessj.tests.StaticHelper.Inner.innerHelp;

/**
 * Tests static imports.
 */
public class StaticImportTest
{
    @Test public void testImportedField ()
    {
        assertTrue(inner.triple(5) == 15);
    }

    @Test public void testImportedMethod ()
    {
        assertTrue(innerHelp(5) == 29);
    }
}
