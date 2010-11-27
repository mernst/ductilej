//
// $Id$

package org.ductilej.tests;

import java.util.AbstractMap;

import org.junit.Test;

/**
 * Tests type resolution of some complex nested type variables.
 */
public class NestedTypeVarTest
{
    static abstract class Impl<K, V, E> extends AbstractMap<K, V>
    {
        final class Segment {
            public E getEntry (Object key) {
                return null;
            }
            public boolean removeEntry(E entry) {
                return false;
            }
        }
    }

    static abstract class ComputingImpl<K, V, E> extends Impl<K, V, E> {
        public void test (Object k) {
            @SuppressWarnings("unchecked") K key = (K) k;
            Segment segment = null; // TODO
            E entry = segment.getEntry(key);
            segment.removeEntry(entry);
        }
    }

    @Test public void testNoop ()
    {
        // nothing to test here, we just want to resolve types above
    }
}
