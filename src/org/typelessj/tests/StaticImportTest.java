//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

import static org.typelessj.tests.StaticHelper.inner;

/**
 * Tests static imports.
 */
public class StaticImportTest
{
    @Test public void testStaticImportedField ()
    {
        assertTrue(inner.triple(5) == 15);
    }
}
