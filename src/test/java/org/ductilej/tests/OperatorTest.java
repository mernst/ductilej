//
// $Id$

package org.ductilej.tests;

import java.io.File;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests operator transformation.
 */
public class OperatorTest
{
    @Test public void testUnary ()
    {
        int ii = 1;
        assertEquals(1, ii++);
        assertEquals(3, ++ii);
        assertEquals(3, ii--);
        assertEquals(1, --ii);

        int[] values = new int[1];
        assertEquals(0, values[0]++);
        assertEquals(2, ++values[0]);
        assertEquals(2, values[0]--);
        assertEquals(0, --values[0]);

        Value value = new Value();
        assertEquals(0, value.value++);
        assertEquals(2, ++value.value);
        assertEquals(2, value.value--);
        assertEquals(0, --value.value);
    }

    @Test public void testAssignOp ()
    {
        int ii = 1;
        ii += 1;
        assertEquals(2, ii);
        ii -= 1;
        assertEquals(1, ii);

        int[] values = new int[1];
        values[0] += 1;
        assertEquals(1, values[0]);

        Value value = new Value();
        value.value += 1;
        assertEquals(1, value.value);
    }

    @Test public void testCoercingAssignOp ()
    {
        short x = 3;
        x *= 4.6; // this will do the multiply, then coerce the result to short
        assertEquals(13, x);
    }

    @Test public void testStringPromote ()
    {
        assertEquals("1", ""+1);
        assertEquals("1", 1+"");
        // TODO: what to do with nulls at runtime? always assume string concat?
        // assertEquals("null1", (String)null+1);
        // assertEquals("1null", 1+(String)null);
        assertEquals("1", ""+'1');
        assertEquals("1", '1'+"");

        // make sure type of '~' + File.separator is String
        assertTrue(("~" + File.separator).startsWith('~' + File.separator));
    }

    @Test public void testShortCircuit ()
    {
        // short circuitry should save us from disaster here
        assertFalse(false && fail());
        assertTrue(true || fail());
    }

    @Test public void testStringAppend ()
    {
        String foo = "foo";
        foo = foo + " bar";
        foo += " baz";
        assertEquals("foo bar baz", foo);
    }

    protected static class Value {
        public int value = 0;
    }

    protected boolean fail () {
        throw new AssertionError();
    }
}
