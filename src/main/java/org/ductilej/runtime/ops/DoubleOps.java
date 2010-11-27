//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Double.
 */
public class DoubleOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Double)arg).doubleValue();
    }
    public Object minus (Object arg) {
        return -((Double)arg).doubleValue();
    }
    public Object increment (Object arg) {
        double tmp = ((Double)arg).doubleValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
        double tmp = ((Double)arg).doubleValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        throw new IllegalArgumentException("Bitwise complement illegal on double");
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on double");
    }
}
