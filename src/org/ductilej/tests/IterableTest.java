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

    // TODO: move these into Ductile-only test suite when we have such a thing
    // @Test public void testDuckTypedIterable () {
    //     Object foo = new Object() {
    //         public Iterator<String> iterator () {
    //             return _list.iterator();
    //         }
    //         protected ArrayList<String> _list = new ArrayList<String>(); {
    //             _list.add("One");
    //         }
    //     };
    //     for (String item : foo) {
    //         assertEquals("One", item);
    //     }
    // }

    // @Test public void testDuckTypedNonIterable () {
    //     Object foo = new Object() {
    //         public String iterator () {
    //             return "Ohai!";
    //         }
    //     };
    //     try {
    //         for (String item : foo) {
    //             assertEquals("One", item);
    //         }
    //         fail();
    //     } catch (IllegalArgumentException iae) {
    //         // expected, because iterator() returns non-Iterator
    //     }
    // }
}
