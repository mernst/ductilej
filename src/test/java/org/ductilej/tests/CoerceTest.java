//
// $Id$

package org.ductilej.tests;

import java.util.Date;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests coercions.
 */
public class CoerceTest
{
    @Test public void testCastCoerce ()
    {
        int a = 0;
        a = (int) (3 * 4.5f);
        assertEquals(13, a);
    }

    @Test public void testImplictEqualityCoercion ()
    {
        short value = (short)0x42F0;
        assertTrue(value == 0x42F0);
    }

    @Test public void testCompareCoercion ()
    {
        byte bvalue = (byte)0;
        char cvalue = (char)0;
        short svalue = (char)0;

        assertTrue(bvalue < 100);
        assertTrue(bvalue <= 0);
        assertTrue(100 > bvalue);
        assertTrue(0 >= bvalue);

        assertTrue(cvalue < 100);
        assertTrue(cvalue <= 0);
        assertTrue(100 > cvalue);
        assertTrue(0 >= cvalue);

        assertTrue(svalue < 100);
        assertTrue(svalue <= 0);
        assertTrue(100 > svalue);
        assertTrue(0 >= svalue);
    }

    @Test public void testCoerceReturnValue ()
    {
        assertEquals(0L, new MyDate().getTime());
    }

    protected static class MyDate extends Date {
        // since this overrides a library method, its signature will not be detyped
        @Override public long getTime () {
            return 0; // javac will normally coerce this int to a long, we must do so as well
        }
    };
}
