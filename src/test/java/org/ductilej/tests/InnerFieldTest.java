//
// $Id$

package org.ductilej.tests;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Ensures correct types are assigned to inner field members.
 */
public class InnerFieldTest
{
    @Test public void testInnerField ()
    {
        Runnable r = new Runnable() {
            String hello = "Hello";
            public void run () {
                add(hello);
            }
        };
        r.run();
        assertEquals("Hello", _list.get(0));
    }

    protected void add (String elem) {
        _list.add(elem);
    }

    protected List<String> _list = new ArrayList<String>();
}
