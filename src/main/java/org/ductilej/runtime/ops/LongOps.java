//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Long.
 */
public class LongOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Long)arg).longValue();
    }
    public Object minus (Object arg) {
        return -((Long)arg).longValue();
    }
    public Object increment (Object arg) {
	long tmp = ((Long)arg).longValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
	long tmp = ((Long)arg).longValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        return ~((Long)arg).longValue();
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on long");
    }
}
