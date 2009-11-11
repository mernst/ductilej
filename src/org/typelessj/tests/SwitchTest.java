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
    @Test public void testLocalSwitch ()
    {
        assertTrue(switchInt(0) == 3);
        assertTrue(switchInt(1) == 1);
        assertTrue(switchInt(2) == 1);
        assertTrue(switchInt(3) == 0);
        assertTrue(switchInt(4) == 1);
    }

    protected int switchInt (int value)
    {
        switch (value) {
        case 0: return 3;
        default:
        case 1:
        case 2: return 1;
        case 3: return 0;
        }
    }
}
