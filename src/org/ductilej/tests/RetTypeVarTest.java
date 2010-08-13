//
// $Id$

package org.ductilej.tests;

import java.util.ArrayList;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetTypeVarTest
{
    public static class MyList<T> extends ArrayList<T> {
        @Override public T get (int index) {
            T val = super.get(index);
            return val;
        }
    }

    public static class MyIntList extends ArrayList<Integer> {
        @Override public Integer get (int index) {
            Integer val = super.get(index);
            return val;
        }
    }

    @Test public void test ()
    {
        MyList<Integer> list = new MyList<Integer>();
        list.add(5);
        assertEquals(Integer.valueOf(5), list.get(0));

        MyIntList ilist = new MyIntList();
        ilist.add(5);
        assertEquals(Integer.valueOf(5), ilist.get(0));
    }
}
