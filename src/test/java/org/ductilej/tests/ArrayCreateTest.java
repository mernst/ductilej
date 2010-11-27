//
// $Id$

package org.ductilej.tests;

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
        Number[][] nums = new Number[][] { { foo, 5f, 5 } };
        assertEquals(nums[0][0], foo);
        assertEquals(nums[0][1], 5f);
        assertEquals(nums[0][2], 5);
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

    @Test public void testArrayInitializer ()
    {
        int[] data = { 1, 2, 3, 4 };
        assertTrue(data[0] == 1);
    }

    @Test public void testByteArrayInit ()
    {
        byte[] b1 = { 0, 1, -128, 44, 12 };
        assertEquals(-128, b1[2]);
    }
}
