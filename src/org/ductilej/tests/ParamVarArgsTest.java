//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of varags with parameterized variable argument. Edge case extraordinaire!
 */
public class ParamVarArgsTest
{
    public static interface Predicate<T> {
        boolean apply (T arg);
    }

    public static <T> Predicate<T> or (final Predicate<? super T>... preds) {
        return new Predicate<T>() {
            public boolean apply (T arg) {
                for (Predicate<? super T> pred : preds) {
                    if (pred.apply(arg)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    @SuppressWarnings("unchecked") // this use of parameterized varargs is safe
    @Test public void testParamVarArgs () {
        Predicate<Integer> test = or(FALSE);
        assertEquals(false, test.apply(1));
    }

    protected static final Predicate<Integer> FALSE = new Predicate<Integer>() {
        public boolean apply (Integer arg) {
            return false;
        }
    };
}
