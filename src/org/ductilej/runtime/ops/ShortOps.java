//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Short.
 */
public class ShortOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Short)arg).shortValue();
    }
    public Object minus (Object arg) {
        return -((Short)arg).shortValue();
    }
    public Object increment (Object arg) {
	short tmp = ((Short)arg).shortValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
	short tmp = ((Short)arg).shortValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        return ~((Short)arg).shortValue();
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on short");
    }
}
