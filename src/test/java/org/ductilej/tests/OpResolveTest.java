//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests resolution of operator types.
 */
public class OpResolveTest
{
    @Test public void testBoolOps ()
    {
        boolean lhs = false, rhs = true;
        consumeBoolean(lhs | rhs);
        consumeBoolean(lhs ^ rhs);
        consumeBoolean(lhs & rhs);

        boolean arg = false;
        consumeBoolean(!arg);
    }

    @Test public void testIntOps ()
    {
        int lhs = 1, rhs = 1;
        consumeInt(lhs + rhs);
        consumeInt(lhs - rhs);
        consumeInt(lhs / rhs);
        consumeInt(lhs * rhs);
        consumeInt(lhs % rhs);

        consumeInt(lhs << rhs);
        consumeInt(lhs >> rhs);
        consumeInt(lhs >>> rhs);

        consumeInt(lhs | rhs);
        consumeInt(lhs ^ rhs);
        consumeInt(lhs & rhs);

        int arg = 1;
        consumeInt(+arg);
        consumeInt(-arg);
        consumeInt(~arg);
    }

    @Test public void testLongOps ()
    {
        long lhs = 1l, rhs = 1l;
        consumeLong(lhs + rhs);
        consumeLong(lhs - rhs);
        consumeLong(lhs / rhs);
        consumeLong(lhs * rhs);
        consumeLong(lhs % rhs);

        consumeLong(lhs << rhs);
        consumeLong(lhs >> rhs);
        consumeLong(lhs >>> rhs);

        consumeLong(lhs | rhs);
        consumeLong(lhs ^ rhs);
        consumeLong(lhs & rhs);

        long arg = 1l;
        consumeLong(+arg);
        consumeLong(-arg);
        consumeLong(~arg);
    }

    @Test public void testFloatOps ()
    {
        float lhs = 1f, rhs = 1f;
        consumeFloat(lhs + rhs);
        consumeFloat(lhs - rhs);
        consumeFloat(lhs / rhs);
        consumeFloat(lhs * rhs);
        consumeFloat(lhs % rhs);

        float arg = 1f;
        consumeFloat(+arg);
        consumeFloat(-arg);
    }

    @Test public void testDoubleOps ()
    {
        double lhs = 1d, rhs = 1d;
        consumeDouble(lhs + rhs);
        consumeDouble(lhs - rhs);
        consumeDouble(lhs / rhs);
        consumeDouble(lhs * rhs);
        consumeDouble(lhs % rhs);

        double arg = 1d;
        consumeDouble(+arg);
        consumeDouble(-arg);
    }

    @Test public void testBoxedIntOps ()
    {
        Integer lhs = 1, rhs = 1;
        consumeInt(lhs + rhs);
        consumeInt(lhs - rhs);
        consumeInt(lhs / rhs);
        consumeInt(lhs * rhs);
        consumeInt(lhs % rhs);

        consumeInt(lhs << rhs);
        consumeInt(lhs >> rhs);
        consumeInt(lhs >>> rhs);

        consumeInt(lhs | rhs);
        consumeInt(lhs ^ rhs);
        consumeInt(lhs & rhs);

        Integer arg = 1;
        consumeInt(+arg);
        consumeInt(-arg);
        consumeInt(~arg);
    }

    @Test public void testBoxedLongOps ()
    {
        Long lhs = 1l, rhs = 1l;
        consumeLong(lhs + rhs);
        consumeLong(lhs - rhs);
        consumeLong(lhs / rhs);
        consumeLong(lhs * rhs);
        consumeLong(lhs % rhs);

        consumeLong(lhs << rhs);
        consumeLong(lhs >> rhs);
        consumeLong(lhs >>> rhs);

        consumeLong(lhs | rhs);
        consumeLong(lhs ^ rhs);
        consumeLong(lhs & rhs);

        Long arg = 1l;
        consumeLong(+arg);
        consumeLong(-arg);
        consumeLong(~arg);
    }

    @Test public void testBoxedFloatOps ()
    {
        Float lhs = 1f, rhs = 1f;
        consumeFloat(lhs + rhs);
        consumeFloat(lhs - rhs);
        consumeFloat(lhs / rhs);
        consumeFloat(lhs * rhs);
        consumeFloat(lhs % rhs);

        Float arg = 1f;
        consumeFloat(+arg);
        consumeFloat(-arg);
    }

    @Test public void testBoxedDoubleOps ()
    {
        Double lhs = 1d, rhs = 1d;
        consumeDouble(lhs + rhs);
        consumeDouble(lhs - rhs);
        consumeDouble(lhs / rhs);
        consumeDouble(lhs * rhs);
        consumeDouble(lhs % rhs);

        Double arg = 1d;
        consumeDouble(+arg);
        consumeDouble(-arg);
    }

    protected void consumeBoolean (Boolean value) {
        assertEquals(Boolean.class, value.getClass());
    }

    protected void consumeInt (Integer value) {
        assertEquals(Integer.class, value.getClass());
    }

    protected void consumeLong (Long value) {
        assertEquals(Long.class, value.getClass());
    }

    protected void consumeFloat (Float value) {
        assertEquals(Float.class, value.getClass());
    }

    protected void consumeDouble (Double value) {
        assertEquals(Double.class, value.getClass());
    }
}
