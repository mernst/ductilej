//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Short and rhs of Character.
 */
public class ShortCharacterOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() + ((Character)rhs).charValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() - ((Character)rhs).charValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() * ((Character)rhs).charValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() / ((Character)rhs).charValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() % ((Character)rhs).charValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() | ((Character)rhs).charValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() & ((Character)rhs).charValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() ^ ((Character)rhs).charValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() << ((Character)rhs).charValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() >> ((Character)rhs).charValue();
    }
    public Object unsignedRightShift (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() >>> ((Character)rhs).charValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() == ((Character)rhs).charValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() < ((Character)rhs).charValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() <= ((Character)rhs).charValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() > ((Character)rhs).charValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() >= ((Character)rhs).charValue();
    }
}
