//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

import org.ductilej.tests.helper.HelperUtil;

/**
 * Tests switch selector transformation.
 */
public class SwitchTest
{
    // we won't detype static final fields
    public static final int ONE = 1;
    public static final int TWO = ONE+1;

    public enum Code { A, B, C };

    @Test public void testLocalSwitch ()
    {
        for (int ii = 0; ii < CASES.length; ii++) {
            assertTrue(switchArg(CASES[ii]) == RESULTS[ii]);
            assertTrue(switchLocal(CASES[ii]) == RESULTS[ii]);
            assertTrue(switchSelect(new Wrapper(CASES[ii])) == RESULTS[ii]);
            assertTrue(switchMethod(CASES[ii]) == RESULTS[ii]);
        }
        assertEquals("B", switchEnum(Code.B));
        assertEquals("A", switchRemoteEnum());
    }

    @Test public void testCastedSwitch ()
    {
        char c = '0';
        int v;
        switch ((int)c) {
        case 0: v = 0; break;
        default: v = 1; break;
        }
        assertEquals(v, 1);
    }

    @Test public void testCharSwitch ()
    {
        char c = '0';
        int v;
        switch (c) {
        case 0: v = 0; break;
        case '0': v = 1; break;
        default: v = 2; break;
        }
        assertEquals(v, 1);
    }

    @Test public void testArithExprSwitch ()
    {
        int v = 10;
        switch (v % 5) {
        case 0: v = 0; break;
        default: v = 1; break;
        }
        assertEquals(v, 0);
    }

    protected int switchArg (int value)
    {
        switch (value) {
        case 0: return 3;
        default:
        case ONE:
        case TWO: return 1;
        case 3: return 0;
        }
    }

    protected int switchLocal (int value)
    {
        int ovalue = value;
        switch (ovalue) {
        case 0: return 3;
        default:
        case ONE:
        case TWO: return 1;
        case 3: return 0;
        }
    }

    protected int switchSelect (Wrapper wrapper)
    {
        switch (wrapper.value) {
        case 0: return 3;
        default:
        case ONE:
        case TWO: return 1;
        case 3: return 0;
        }
    }

    protected int switchMethod (Integer value)
    {
        switch (value.intValue()) {
        case 0: return 3;
        default:
        case ONE:
        case TWO: return 1;
        case 3: return 0;
        }
    }

    protected String switchEnum (Code code)
    {
        switch (code) {
        case A: return "A";
        case B: return "B";
        default:
        case C: return "C";
        }
    }

    protected String switchRemoteEnum ()
    {
        switch (HelperUtil.getA()) {
        case A: return "A";
        case B: return "B";
        default:
        case C: return "C";
        }
    }

    protected static class Wrapper {
        public int value;
        public Wrapper (int value) {
            this.value = value;
        }
    }

    protected static final int[] CASES = { 0, 1, 2, 3, 4 };
    protected static final int[] RESULTS = { 3, 1, 1, 0, 1 };
}
