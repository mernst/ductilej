//
// $Id$

package org.ductilej.tests;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

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

    @Test public void testTypeParamToConcrete ()
    {
        StringWriter out = new StringWriter();
        List<String> strs = new ArrayList<String>();
        strs.add("one");
        strs.add("two");
        strs.add("three");
        for (int ii = 0, ll = strs.size(); ii < ll; ii++) {
            out.write(strs.get(ii)); // A get() -> String get()
        }
        assertEquals(out.toString(), "onetwothree");
    }

    protected static Ord toOrd (String value)
    {
        return Enum.valueOf(Ord.class, value);
    }

    protected static Ord toOrd (int value)
    {
        switch (value) {
        case 1: return Ord.ONE;
        case 2: return Ord.TWO;
        case 3: return Ord.THREE;
        default: throw new IllegalArgumentException();
        }
    }
}
