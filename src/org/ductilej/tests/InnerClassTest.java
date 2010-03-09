//
// $Id$

package org.ductilej.tests;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests anonymous inner-class handling.
 */
public class InnerClassTest
{
    public interface Value {
        int value ();
        int op (int value);
    }

    @Test public void testDetypedInterface () {
        Value v = new Value() {
            public int value () {
                return 5;
            }
            public int op (int value) {
                // trigger isStaticReceiver processing
                return Integer.valueOf(String.valueOf(value)) * 5;
            }
        };
        assertEquals(25, v.op(v.value()));
    }

    @Test public void testNonDetypedInterface () {
        Integer[] values = new Integer[] { 9, 8, 7, 6, 5, 4, 3, 2, 1 };
        Arrays.sort(values, new Comparator<Integer>() {
            public int compare (Integer a, Integer b) {
                return (a == b) ? 0 : ((a < b) ? -1 : 1);
            }
        });
        assertEquals((Integer)1, values[0]);
        assertEquals((Integer)3, values[2]);
    }

    @Test public void testAnonLibraryInst () {
        int size = 25;
        Map<Integer,Integer> map = new HashMap<Integer,Integer>(size) {
            // nothing to see here, move it along
        };
        map.put(5, 10);
        assertEquals((Integer)10, map.get(5));
    }

    @Test public void testLocalInNonStaticContext () {
        class Foo {
            public int bar;
            public Foo (int bar) {
                this.bar = bar;
            }
        }
        assertEquals(5, new Foo(5).bar);
    }

    @Test public void testLocalIntStaticContext () {
        assertEquals(42, testStaticLocal(42));
    }

    @Test public void testInnerInInner () throws Exception {
        Callable<Integer> c = new Callable<Integer>() {
            public Integer call () throws Exception {
                Value v = new Value() {
                    public int value () {
                        return 42;
                    }
                    public int op (int value) {
                        return value;
                    }
                };
                return v.op(v.value());
            }
        };
        assertEquals((Integer)42, c.call());
    }

    protected static int testStaticLocal (int value) {
        class LocalInStaticContext {
            public int foo;
            public LocalInStaticContext (int foo) {
                this.foo = foo;
            }
        }
        return new LocalInStaticContext(value).foo;
    }

    // test inner class handling with no enclosing member
    protected Value _value = new Value() {
        public int value () {
            return 3;
        }
        public int op (int value) {
            return value+1;
        }
    };
}
