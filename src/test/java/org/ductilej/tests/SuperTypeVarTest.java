//
// $Id$

package org.ductilej.tests;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests type variables accessed through a super member.
 */
public class SuperTypeVarTest
{
    public class A<E> {
        public List<E> list = new ArrayList<E>();
    }

    public class B<T> extends A<T> {
        public void add (T elem) {
            list.add(elem);
        }
    }

    @Test public void testSuperTypeVar ()
    {
        B<String> b = new B<String>();
        b.add("Hello");
        assertEquals("Hello", b.list.get(0));
    }
}
