//
// $Id$

package org.ductilej.tests;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling typing of cloned arrays.
 */
public class ArrayCloneTest
{
    @Test public void testClonedArray ()
    {
        String[] array = { "one", "two", "three" };
        // make sure the type of array.clone() is being resolved as String[] rather than Object
        // (which is what is declared and what would be correct in the absence of special cases)
        List<String> list = Arrays.asList(array.clone());
        assertEquals(3, list.size());
    }
}
