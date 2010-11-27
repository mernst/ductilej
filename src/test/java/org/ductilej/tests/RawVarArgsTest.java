//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests our varargs machinations in the presence of raw types (which people unfortunately use, so
 * we have to handle them reasonably).
 */
public class RawVarArgsTest
{
    public static interface Predicate<T> {
        boolean apply (T arg);
    }

    public static <T> Predicate<T> and (final Predicate<? super T>... preds) {
        return new Predicate<T>() {
            public boolean apply (T arg) {
                for (Predicate<? super T> pred : preds) {
                    if (!pred.apply(arg)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    public void testRawVarArgs () {
        Predicate[] array = { FALSE };
        Predicate<Object> predicate = and(array);
        assertFalse(predicate.apply(1));
    }

    protected static final Predicate<Object> FALSE = new Predicate<Object>() {
        public boolean apply (Object arg) {
            return false;
        }
    };
}
