//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Integer and rhs of Byte.
 */
public class IntegerByteOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() + ((Byte)rhs).byteValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() - ((Byte)rhs).byteValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() * ((Byte)rhs).byteValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() / ((Byte)rhs).byteValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() % ((Byte)rhs).byteValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() | ((Byte)rhs).byteValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() & ((Byte)rhs).byteValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() ^ ((Byte)rhs).byteValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() << ((Byte)rhs).byteValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() >> ((Byte)rhs).byteValue();
    }
    public Object unsignedRightShift (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() >>> ((Byte)rhs).byteValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() == ((Byte)rhs).byteValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() < ((Byte)rhs).byteValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() <= ((Byte)rhs).byteValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() > ((Byte)rhs).byteValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Integer)lhs).intValue() >= ((Byte)rhs).byteValue();
    }
}
