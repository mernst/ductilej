//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Byte.
 */
public class ByteOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Byte)arg).byteValue();
    }
    public Object minus (Object arg) {
        return -((Byte)arg).byteValue();
    }
    public Object increment (Object arg) {
	byte tmp = ((Byte)arg).byteValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
	byte tmp = ((Byte)arg).byteValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        return ~((Byte)arg).byteValue();
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on byte");
    }
}
