//
// $Id$

package org.ductilej.tests;

import java.lang.reflect.Constructor;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Does something extraordinary.
 */
public class TypeVarInfTest
{
    public static class Value
    {
    }

    public static <K, V> Function<K, V> newInstanceCreator (Class<V> clazz)
    {
        final Constructor<V> ctor;
        try {
            ctor = clazz.getConstructor();
        } catch (NoSuchMethodException nsme) {
            throw new IllegalArgumentException(clazz + " must have a no-args constructor.");
        }
        return new Function<K, V>() {
            public V apply (K key) {
                try {
                    return ctor.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Test public void testTVI ()
    {
        // this triggers some complex type inference, which is what we're testing
        Map<String, Value> test = new MapMaker().makeComputingMap(newInstanceCreator(Value.class));
        Value v = test.get("test");
        assertTrue(v == test.get("test"));
    }
}
