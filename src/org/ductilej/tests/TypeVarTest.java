//
// $Id$

package org.ductilej.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

        @Override
        public boolean add (A element)
        {
            // test whether we correctly determine that the below add() is a method defined by our
            // supertype (which involves some annoying manual type variable translation)
            add(size(), element);
            return true;
        }
    }

    public class Tuple<L,R> {
        public final L left;
        public final R right;
        public Tuple (L left, R right) {
            this.left = left;
            this.right = right;
        }
    }

    @Test public void testSuperCast () {
        MyList<Integer> list = new MyList<Integer>();
        list.add(5);
        assertEquals(Integer.valueOf(5), list.get(0));
    }

    @Test public void testTVarInstantiate () {
        List<Integer> intlist = new ArrayList<Integer>();
        Integer[] intvec = new Integer[] { 1, 2, 3, 4 };
        intlist.addAll(Arrays.asList(intvec));
        assertEquals(Integer.valueOf(1), intlist.get(0));
    }

    @Test public void testTypeVar () {
        Tuple<Integer,Integer> tup = new Tuple<Integer, Integer>(5, 5);
        assertEquals(5, tup.left.intValue());
    }

    @Test public void testQuantifiedPrimitive () {
        assertEquals(5, noop(5));
    }

    protected static <K,V> int countKeyLengths (Map<K, V> map)
    {
        int length = 0;
        for (Map.Entry<K,V> entry : map.entrySet()) {
            // tests having a receiver with type 'K'
            K key = entry.getKey();
            length = length + key.toString().length();
        }
        return length;
    }

    // tests having a quantified primitive as a return type (Resolve has to handle such types
    // specially)
    protected static <T> int noop (int arg)
    {
        return arg;
    }
}
