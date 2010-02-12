//
// $Id$

package org.typelessj.tests;

import java.util.ArrayList;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetTypeVarTest
{
    public static class MyList<T> extends ArrayList<T> {
        public T get (int index) {
            T val = super.get(index);
            return val;
        }
    }

    @Test public void test ()
    {
        MyList<Integer> list = new MyList<Integer>();
        list.add(5);
        assertEquals(Integer.valueOf(5), list.get(0));
    }
}
