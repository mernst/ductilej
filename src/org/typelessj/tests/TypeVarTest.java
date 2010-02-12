//
// $Id$

package org.typelessj.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

        // iter.next() will resolve to type ? which we need to promote to Object before trying to
        // resolve the type of String.valueOf(); this tests that rigamarole
        for (Iterator<?> iter = foo.values().iterator(); iter.hasNext(); ) {
            assertEquals("5", String.valueOf(iter.next()));
        }
    }

    @Test public void testTVarInstantiate () {
        List<Integer> intlist = new ArrayList<Integer>();
        Integer[] intvec = new Integer[] { 1, 2, 3, 4 };
        intlist.addAll(Arrays.asList(intvec));
        assertEquals(Integer.valueOf(1), intlist.get(0));
    }

    public static <K,V> int countKeyLengths (Map<K, V> map)
    {
        int length = 0;
        for (Map.Entry<K,V> entry : map.entrySet()) {
            // tests having a receiver with type 'K'
            K key = entry.getKey();
            length = length + key.toString().length();
        }
        return length;
    }

    public static <T, C extends Collection<T>> C addAll (C col, Enumeration<? extends T> enm)
    {
        while (enm.hasMoreElements()) {
            col.add(enm.nextElement());
        }
        return col;
    }
}
