//
// $Id$

package org.ductilej.tests;

import java.util.ArrayList;
import java.util.Iterator;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests our handling of iterables.
 */
public class IterableTest
{
    @Test public void testList () {
        ArrayList<String> list = new ArrayList<String>();
        list.add("One");
        for (String item : list) {
            assertEquals("One", item);
        }
    }

    @Test public void testArray () {
        String[] array = new String[] { "One" };
        for (String item : array) {
            assertEquals("One", item);
        }
    }
}
