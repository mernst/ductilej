//
// $Id$

package org.typelessj.tests;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests wildcard types.
 */
public class WildcardTest
{
    @Test public void testWildcard () {
        Class<?> clazz = WildcardTest.class;
        assertEquals("WildcardTest", clazz.getSimpleName());

        Map<Class<?>, Integer> foo = new HashMap<Class<?>, Integer>();
        foo.put(WildcardTest.class, 5);
        assertEquals(Integer.valueOf(5), foo.get(WildcardTest.class));

        // iter.next() will resolve to type ? which we need to promote to Object before trying to
        // resolve the type of String.valueOf(); this tests that rigamarole
        for (Iterator<?> iter = foo.values().iterator(); iter.hasNext(); ) {
            assertEquals("5", String.valueOf(iter.next()));
        }
    }

    public static <T, C extends Collection<T>> C addAll (C col, Enumeration<? extends T> enm)
    {
        while (enm.hasMoreElements()) {
            col.add(enm.nextElement());
        }
        return col;
    }
}
