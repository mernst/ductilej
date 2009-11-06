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
        Number[][] nums2 = new Number[3][5];
    }
}
