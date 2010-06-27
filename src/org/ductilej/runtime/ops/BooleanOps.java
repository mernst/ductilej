//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Boolean.
 */
public class BooleanOps implements UnOps
{
    public Object plus (Object arg) {
        throw new IllegalArgumentException("Plus illegal on boolean");
    }
    public Object minus (Object arg) {
        throw new IllegalArgumentException("Minus illegal on boolean");
    }
    public Object increment (Object arg) {
        throw new IllegalArgumentException("Increment illegal on boolean");
    }
    public Object decrement (Object arg) {
        throw new IllegalArgumentException("Decrement illegal on boolean");
    }
    public Object bitComp (Object arg) {
        throw new IllegalArgumentException("Bitwise complement illegal on boolean");
    }
    public Object logicalComp (Object arg) {
        return !((Boolean)arg).booleanValue();
    }
}
