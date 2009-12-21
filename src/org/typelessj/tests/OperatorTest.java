//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests operator transformation.
 */
public class OperatorTest
{
    @Test public void testUnary ()
    {
        int ii = 1;
        assertEquals(ii++, 1);
        assertEquals(++ii, 3);
        assertEquals(ii--, 3);
        assertEquals(--ii, 1);
    }

    @Test public void testAssignOp ()
    {
        int ii = 1;
        ii += 1;
        assertEquals(ii, 2);
        ii -= 1;
        assertEquals(ii, 1);
    }
}
