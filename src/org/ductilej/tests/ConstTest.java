//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests detection of constants.
 */
public class ConstTest
{
    public static final int ONE = 1;
    public static final int TWO = ONE+1;
    public static final int THREE = 2*ONE+1;

    @Test public void testConst ()
    {
        int one = ONE;
        final int two = TWO;
        final int three = one + two;
        final int four = TWO + TWO;
        switch (one) {
        case ONE: break;
        case two: fail(); break;
        case THREE: fail(); break;
        case four: fail(); break;
        }
        assertEquals(3, three);
    }
}
