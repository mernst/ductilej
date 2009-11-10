//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests array creation and assignment.
 */
public class ArrayCreateTest
{
    @Test public void testArrayCreate ()
    {
        Integer foo = new Integer(5);
        Number[][] nums = new Number[][] { { 4, 5f, 5 } };
    }

    @Test public void testArrayCreateDimsCast ()
    {
        int dim1 = 3, dim2 = 5;
        Number[][] nums2 = new Number[dim1][dim2];
        assertTrue(nums2.length == dim1);
        if (dim1 > 0) {
            assertTrue(nums2[0].length == dim2);
        }
    }
}
