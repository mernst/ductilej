//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the resolution of static method overloads.
 */
public class OverloadTest
{
    public static enum Ord { ONE, TWO, THREE };

    public static class Overloaded {
        public Ord toOrd (String value) {
            return OverloadTest.toOrd(value);
        }

        public Ord toOrd (int value) {
            return OverloadTest.toOrd(value);
        }
    }

    @Test public void testSwitchExprOverload ()
    {
        Overloaded o = new Overloaded();
        switch (o.toOrd(2)) {
        case ONE: assertTrue(false); break;
        case TWO: assertTrue(true); break;
        case THREE: assertTrue(false); break;
        }
    }

    protected Ord toOrd (String value)
    {
        return Enum.valueOf(Ord.class, value);
    }

    protected Ord toOrd (int value)
    {
        switch (value) {
        case 1: return Ord.ONE;
        case 2: return Ord.TWO;
        case 3: return Ord.THREE;
        default: throw new IllegalArgumentException();
        }
    }
}
