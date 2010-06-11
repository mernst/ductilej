//
// $Id$

package org.ductilej.tests;

import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of various foreach expressions.
 */
public class ForeachTest
{
    @Test public void testBoolean () {
        for (boolean v : new boolean[] { false, false }) {
            assertTrue(!v);
        }
    }

    @Test public void testByte () {
        for (byte v : new byte[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testShort () {
        for (short v : new short[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testChar () {
        for (char v : new char[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testInt () {
        for (int v : new int[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testLong () {
        for (long v : new long[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testFloat () {
        for (float v : new float[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testDouble () {
        for (double v : new double[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testInteger () {
        for (Integer v : new Integer[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }

    @Test public void testList () {
        for (int v : Arrays.asList(new Integer[] { 0, 0 })) {
            assertTrue(v == 0);
        }
    }

    @Test public void testUnboxedInteger () {
        for (int v : new Integer[] { 0, 0 }) {
            assertTrue(v == 0);
        }
    }
}
