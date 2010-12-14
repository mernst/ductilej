//
// $Id$

package org.ductilej.runtime;

/**
 * Encapsulates unary operations on primitive types. Specialized instances of this class are
 * automatically generated for all primitive types for reasonably fast dispatch of ad-hoc
 * polymorphic unary operators.
 */
public interface UnOps
{
    public Object plus (Object arg);
    public Object minus (Object arg);
    public Object increment (Object arg);
    public Object decrement (Object arg);
    public Object bitComp (Object arg);
    public Object logicalComp (Object arg);
}
