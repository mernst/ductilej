//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests initialization of transformed primitive fields.
 */
public class FieldInitTest
{
    @Test public void testInit ()
    {
        assertEquals(false, boolField);
        assertEquals((byte)0, byteField);
        assertEquals((char)0, charField);
        assertEquals(0, intField);
        assertEquals(0L, longField);
        assertEquals(0f, floatField, 0); // third arg is allowable error
        assertEquals(0d, doubleField, 0); // third arg is allowable error
        assertEquals(null, objectField);
    }

    protected boolean boolField;
    protected byte byteField;
    protected char charField;
    protected int intField;
    protected long longField;
    protected float floatField;
    protected double doubleField;
    protected Object objectField;
}
