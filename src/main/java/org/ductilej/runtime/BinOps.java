//
// $Id$

package org.ductilej.runtime;

/**
 * Encapsulates binary operations on primitive types. Specialized instances of this class are
 * automatically generated for the cartesian product of all compatible primitive types for
 * reasonably fast dispatch of ad-hoc polymorphic binary operators.
 */
public interface BinOps
{
    public Object plus (Object lhs, Object rhs);
    public Object minus (Object lhs, Object rhs);
    public Object multiply (Object lhs, Object rhs);
    public Object divide (Object lhs, Object rhs);
    public Object remainder (Object lhs, Object rhs);

    public Object bitOr (Object lhs, Object rhs);
    public Object bitAnd (Object lhs, Object rhs);
    public Object bitXor (Object lhs, Object rhs);

    public Object leftShift (Object lhs, Object rhs);
    public Object rightShift (Object lhs, Object rhs);
    public Object unsignedRightShift (Object lhs, Object rhs);

    public boolean equalTo (Object lhs, Object rhs);
    public boolean lessThan (Object lhs, Object rhs);
    public boolean lessThanEq (Object lhs, Object rhs);
    public boolean greaterThan (Object lhs, Object rhs);
    public boolean greaterThanEq (Object lhs, Object rhs);
}
