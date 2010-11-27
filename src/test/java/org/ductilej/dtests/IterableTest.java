//
// $Id$

package org.ductilej.dtests;

import java.util.ArrayList;
import java.util.Iterator;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of duck-typed iterables.
 */
public class IterableTest
{
    @Test public void testDuckTypedIterable () {
        Object foo = new Object() {
            public Iterator<String> iterator () {
                return _list.iterator();
            }
            protected ArrayList<String> _list = new ArrayList<String>(); {
                _list.add("One");
            }
        };
        for (String item : foo) {
            assertEquals("One", item);
        }
    }

    @Test(expected=ClassCastException.class)
    public void testDuckTypedNonIterable () {
        Object foo = new Object() {
            public String iterator () {
                return "Ohai!";
            }
        };
        // will throw CCE, because iterator() returns non-Iterator
        for (String item : foo) {
            assertEquals("One", item);
        }
    }
}
