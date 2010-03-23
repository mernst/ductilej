//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Long and rhs of Short.
 */
public class LongShortOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() + ((Short)rhs).shortValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() - ((Short)rhs).shortValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() * ((Short)rhs).shortValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() / ((Short)rhs).shortValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() % ((Short)rhs).shortValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() | ((Short)rhs).shortValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() & ((Short)rhs).shortValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() ^ ((Short)rhs).shortValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() << ((Short)rhs).shortValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >> ((Short)rhs).shortValue();
    }
    public Object unsignedRightShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >>> ((Short)rhs).shortValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() == ((Short)rhs).shortValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() < ((Short)rhs).shortValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() <= ((Short)rhs).shortValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() > ((Short)rhs).shortValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >= ((Short)rhs).shortValue();
    }
}
