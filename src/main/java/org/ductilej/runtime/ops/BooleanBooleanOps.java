//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.BinOps;

/**
 * Implements binary operations for lhs of Boolean and rhs of Boolean.
 */
public class BooleanBooleanOps implements BinOps
{
    public Object plus (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Plus illegal on boolean");
    }
    public Object minus (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Minus illegal on boolean");
    }
    public Object multiply (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Multiply illegal on boolean");
    }
    public Object divide (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Divide illegal on boolean");
    }
    public Object remainder (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Remainder illegal on boolean");
    }

    public Object bitOr (Object lhs, Object rhs) {
        return ((Boolean)lhs).booleanValue() | ((Boolean)rhs).booleanValue();
    }
    public Object bitAnd (Object lhs, Object rhs) {
        return ((Boolean)lhs).booleanValue() & ((Boolean)rhs).booleanValue();
    }
    public Object bitXor (Object lhs, Object rhs) {
        return ((Boolean)lhs).booleanValue() ^ ((Boolean)rhs).booleanValue();
    }

    public Object leftShift (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Left shift illegal on boolean");
    }
    public Object rightShift (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Right shift illegal on boolean");
    }
    public Object unsignedRightShift (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Unsigned right shift illegal on boolean");
    }

    public boolean equalTo (Object lhs, Object rhs) {
        return ((Boolean)lhs).booleanValue() == ((Boolean)rhs).booleanValue();
    }
    public boolean lessThan (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Less than illegal on boolean");
    }
    public boolean lessThanEq (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Less than equal illegal on boolean");
    }
    public boolean greaterThan (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Greater than illegal on boolean");
    }
    public boolean greaterThanEq (Object lhs, Object rhs) {
        throw new IllegalArgumentException("Greater than equal illegal on boolean");
    }
}
