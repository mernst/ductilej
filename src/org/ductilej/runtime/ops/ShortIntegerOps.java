//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Short and rhs of Integer.
 */
public class ShortIntegerOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() + ((Integer)rhs).intValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() - ((Integer)rhs).intValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() * ((Integer)rhs).intValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() / ((Integer)rhs).intValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() % ((Integer)rhs).intValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() | ((Integer)rhs).intValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() & ((Integer)rhs).intValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() ^ ((Integer)rhs).intValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() << ((Integer)rhs).intValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() >> ((Integer)rhs).intValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() == ((Integer)rhs).intValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() < ((Integer)rhs).intValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() <= ((Integer)rhs).intValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() > ((Integer)rhs).intValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Short)lhs).shortValue() >= ((Integer)rhs).intValue();
    }
}
