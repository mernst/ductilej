//
// $Id$

package org.typelessj.tests;

import java.util.ArrayList;

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
        assertEquals(list.get(0), Integer.valueOf(5));
    }
}
