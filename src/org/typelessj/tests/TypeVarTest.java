//
// $Id$

package org.typelessj.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the handling of type variables.
 */
public class TypeVarTest
{
    public static class MyList<A> extends ArrayList<A>
    {
        public void wibble (A value) {
            super.add(value);
        }
    }

    @Test public void testSuperCast () {
        MyList<Integer> list = new MyList<Integer>();
        list.add(5);
        assertEquals(Integer.valueOf(5), list.get(0));
    }

    @Test public void testWildcard () {
        Class<?> clazz = TypeVarTest.class;
        assertEquals("TypeVarTest", clazz.getSimpleName());

        Map<Class<?>, Integer> foo = new HashMap<Class<?>, Integer>();
        foo.put(TypeVarTest.class, 5);
        assertEquals(Integer.valueOf(5), foo.get(TypeVarTest.class));
    }
}
