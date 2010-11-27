//
// $Id$

package org.ductilej.tests;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests varargs handling involving nulls.
 */
public class NullVarArgsTest
{
    @Test public void testNullDisambig ()
    {
        assertEquals(-1, varargs((Object[])null));
        assertEquals(0, varargs());
        assertEquals(1, varargs(1));
        // this should get wrapped into a one element array containing null
        assertEquals(1, varargs((Object)null));
    }

    @Test public void testVarArgsArrayType ()
    {
        List<Integer> list = Arrays.asList((Integer) null);
        Object[] array = list.toArray();
        assertEquals(array.getClass(), Integer[].class);
    }

    @Test public void testLibraryNullVarArray ()
        throws NoSuchMethodException
    {
        Constructor<?> ctor = getClass().getDeclaredConstructor((Class<?>[]) null);
        assertTrue(ctor != null);
    }

    @Test public void testNonVarArgsToVarArgs ()
    {
        assertEquals("[null]", toString(new Object[] { null }));
    }

    protected static int varargs (Object... args)
    {
        return (args == null) ? -1 : args.length;
    }

    protected static String toString (Object[] elems) {
        return Arrays.asList(elems).toString();
    }
}
