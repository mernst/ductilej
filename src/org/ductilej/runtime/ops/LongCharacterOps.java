//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.Ops;

/**
 * Implements binary operations with for lhs of Long and rhs of Character.
 */
public class LongCharacterOps implements Ops
{
    public Object plus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() + ((Character)rhs).charValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() - ((Character)rhs).charValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() * ((Character)rhs).charValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() / ((Character)rhs).charValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() % ((Character)rhs).charValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() | ((Character)rhs).charValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() & ((Character)rhs).charValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() ^ ((Character)rhs).charValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() << ((Character)rhs).charValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >> ((Character)rhs).charValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() == ((Character)rhs).charValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() < ((Character)rhs).charValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() <= ((Character)rhs).charValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() > ((Character)rhs).charValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >= ((Character)rhs).charValue();
    }
}
