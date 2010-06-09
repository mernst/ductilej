//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Integer.
 */
public class IntegerOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Integer)arg).intValue();
    }
    public Object minus (Object arg) {
        return -((Integer)arg).intValue();
    }
    public Object increment (Object arg) {
	int tmp = ((Integer)arg).intValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
	int tmp = ((Integer)arg).intValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        return ~((Integer)arg).intValue();
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on int");
    }
}
