//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.Ops;

/**
 * Implements binary operations with for lhs of Character and rhs of Long.
 */
public class CharacterLongOps implements Ops
{
    public Object plus (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() + ((Long)rhs).longValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() - ((Long)rhs).longValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() * ((Long)rhs).longValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() / ((Long)rhs).longValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() % ((Long)rhs).longValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() | ((Long)rhs).longValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() & ((Long)rhs).longValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() ^ ((Long)rhs).longValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() << ((Long)rhs).longValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() >> ((Long)rhs).longValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() == ((Long)rhs).longValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() < ((Long)rhs).longValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() <= ((Long)rhs).longValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() > ((Long)rhs).longValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Character)lhs).charValue() >= ((Long)rhs).longValue();
    }
}
