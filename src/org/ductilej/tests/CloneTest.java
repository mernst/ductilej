//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests detection of clone methods.
 */
public class CloneTest
{
    @Test public void testArrayClone ()
    {
        int[] vals = { 1, 2, 3, 4 };
        int[] cvals = vals.clone();
        assertEquals(vals[1], cvals[1]);
    }
}
