//
// $Id$

package org.ductilej.runtime.ops;

import org.ductilej.runtime.UnOps;

/**
 * Implements unary operations for Character.
 */
public class CharacterOps implements UnOps
{
    public Object plus (Object arg) {
        return +((Character)arg).charValue();
    }
    public Object minus (Object arg) {
        return -((Character)arg).charValue();
    }
    public Object increment (Object arg) {
	char tmp = ((Character)arg).charValue();
        return ++tmp;
    }
    public Object decrement (Object arg) {
	char tmp = ((Character)arg).charValue();
        return --tmp;
    }
    public Object bitComp (Object arg) {
        return ~((Character)arg).charValue();
    }
    public Object logicalComp (Object arg) {
        throw new IllegalArgumentException("Logical complement illegal on char");
    }
}
