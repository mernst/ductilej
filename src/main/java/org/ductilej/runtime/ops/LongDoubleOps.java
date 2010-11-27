//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Long and rhs of Double.
 */
public class LongDoubleOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() + ((Double)rhs).doubleValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() - ((Double)rhs).doubleValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() * ((Double)rhs).doubleValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() / ((Double)rhs).doubleValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() % ((Double)rhs).doubleValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        throw new RuntimeException("Cannot bitwise-or LEFT and RIGHT");
    }
    public Object bitAnd (Object lhs, Object rhs) {
        throw new RuntimeException("Cannot bitwise-and LEFT and RIGHT");
    }
    public Object bitXor (Object lhs, Object rhs) {
        throw new RuntimeException("Cannot bitwise-xor LEFT and RIGHT");
    }

    public Object leftShift (Object lhs, Object rhs) {
        throw new RuntimeException("Cannot left-shift LEFT and RIGHT");
    }
    public Object rightShift (Object lhs, Object rhs) {
        throw new RuntimeException("Cannot right-shift LEFT and RIGHT");
    }
    public Object unsignedRightShift (Object lhs, Object rhs) {
        throw new RuntimeException("Cannot unsigned right-shift LEFT and RIGHT");
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() == ((Double)rhs).doubleValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() < ((Double)rhs).doubleValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() <= ((Double)rhs).doubleValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() > ((Double)rhs).doubleValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >= ((Double)rhs).doubleValue();
    }
}
