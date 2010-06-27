//
// $Id$

package org.ductilej.tests;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of static blocks.
 */
public class StaticBlockTest
{
    @Test public void testImportedField ()
    {
        assertEquals(2, _classes.size());
    }

    protected static Set<Class<?>> _classes;
    static {
        Set<Class<?>> set = new HashSet<Class<?>>();
        set.add(Byte.TYPE);
        set.add(Short.TYPE);
        _classes = set;
    }
}
