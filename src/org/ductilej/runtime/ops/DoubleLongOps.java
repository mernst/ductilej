//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Double and rhs of Long.
 */
public class DoubleLongOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() + ((Long)rhs).longValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() - ((Long)rhs).longValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() * ((Long)rhs).longValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() / ((Long)rhs).longValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() % ((Long)rhs).longValue();
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
        return ((Double)lhs).doubleValue() == ((Long)rhs).longValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() < ((Long)rhs).longValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() <= ((Long)rhs).longValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() > ((Long)rhs).longValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Double)lhs).doubleValue() >= ((Long)rhs).longValue();
    }
}
