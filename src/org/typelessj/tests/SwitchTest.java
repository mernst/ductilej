//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests switch selector transformation.
 */
public class SwitchTest
{
    // we won't detype static final fields
    public static final int ONE = 1;

    @Test public void testLocalSwitch ()
    {
        for (int ii = 0; ii < CASES.length; ii++) {
            assertTrue(switchArg(CASES[ii]) == RESULTS[ii]);
            assertTrue(switchLocal(CASES[ii]) == RESULTS[ii]);
            assertTrue(switchSelect(new Wrapper(CASES[ii])) == RESULTS[ii]);
            assertTrue(switchMethod(CASES[ii]) == RESULTS[ii]);
        }
    }

    protected int switchArg (int value)
    {
        switch (value) {
        case 0: return 3;
        default:
        case ONE:
        case 2: return 1;
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
        case 2: return 1;
        case 3: return 0;
        }
    }

    protected int switchSelect (Wrapper wrapper)
    {
        switch (wrapper.value) {
        case 0: return 3;
        default:
        case ONE:
        case 2: return 1;
        case 3: return 0;
        }
    }

    protected int switchMethod (Integer value)
    {
        switch (value.intValue()) {
        case 0: return 3;
        default:
        case ONE:
        case 2: return 1;
        case 3: return 0;
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
