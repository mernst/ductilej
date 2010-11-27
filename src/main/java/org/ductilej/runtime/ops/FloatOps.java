//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Float.
 */
public class FloatOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Float)arg).floatValue();
    }
    public Object minus (Object arg) {
        return -((Float)arg).floatValue();
    }
    public Object increment (Object arg) {
        float tmp = ((Float)arg).floatValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
        float tmp = ((Float)arg).floatValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        throw new IllegalArgumentException("Bitwise complement illegal on float");
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on float");
    }
}
