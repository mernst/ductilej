//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.Ops;

/**
 * Implements binary operations with for lhs of Long and rhs of Byte.
 */
public class LongByteOps implements Ops
{
    public Object plus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() + ((Byte)rhs).byteValue();
    }
    public Object minus (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() - ((Byte)rhs).byteValue();
    }
    public Object multiply (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() * ((Byte)rhs).byteValue();
    }
    public Object divide (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() / ((Byte)rhs).byteValue();
    }
    public Object remainder (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() % ((Byte)rhs).byteValue();
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() | ((Byte)rhs).byteValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() & ((Byte)rhs).byteValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() ^ ((Byte)rhs).byteValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() << ((Byte)rhs).byteValue();
    }
    public Object rightShift (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >> ((Byte)rhs).byteValue();
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() == ((Byte)rhs).byteValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() < ((Byte)rhs).byteValue();
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() <= ((Byte)rhs).byteValue();
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() > ((Byte)rhs).byteValue();
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        return ((Long)lhs).longValue() >= ((Byte)rhs).byteValue();
    }
}
