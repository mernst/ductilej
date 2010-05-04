//
// $Id$

package org.ductilej.tests;

import java.util.concurrent.Callable;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of a parameterized 'this' reference.
 */
public class ParameterizedThisTest
{
    public static abstract class A<E> {
        public abstract E getElement ();
    }

    public static abstract class B<V> extends A<Callable<V>> {
        public V getValue () throws Exception {
            // we're testing the resolution of the 'this' expression which should have type B<V>
            // rather than just a bare B, if it has the latter, then getElement() will have a
            // return type of Object rather than V
            return this.getElement().call();
        }
    }

    @Test public void testPT () throws Exception
    {
        B<String> b = new B<String>() {
            public Callable<String> getElement () {
                return new Callable<String>() {
                    public String call () {
                        return "Yay!";
                    }
                };
            }
        };
        assertEquals("Yay!", b.getValue());
    }
}
