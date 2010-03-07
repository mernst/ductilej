//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests resolution of fully-qualified class names.
 */
public class QualifiedNameTest
{
    @Test public void testConstant ()
    {
        assertEquals("Center", java.awt.BorderLayout.CENTER);
    }
}
