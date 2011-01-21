//
// $Id$

package org.ductilej.tests;

import java.io.*;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the special handling for unserializing transient primitive fields.
 */
public class SerializePrimsTest
{
    public static class Record implements Serializable
    {
        public transient boolean booleanValue;
        public transient byte byteValue;
        public transient short shortValue;
        public transient char charValue;
        public transient int intValue;
        public transient long longValue;
        public transient float floatValue;
        public transient double doubleValue;
    }

    public static class CustomRecord implements Serializable
    {
        // these initializations will *not* be done automatically when this object is unserialized
        // (whether detyped or not), but we're mimicking a situation where a programmer chooses to
        // have a transient field for which they provide both a constructor-based initializer and a
        // readObject-based initializer, and we want to make sure that we don't step on their toes
        public transient boolean booleanValue = true;
        public transient byte byteValue = 1;
        public transient short shortValue = 1;
        public transient char charValue = 1;
        public transient int intValue = 1;
        public transient long longValue = 1;
        public transient float floatValue = 1;
        public transient double doubleValue = 1;

        // we want to make sure that these non-transient values are still properly handled by the
        // call to in.defaultReadObject() below
        public boolean booleanValueS = true;
        public byte byteValueS = 1;
        public short shortValueS = 1;
        public char charValueS = 'a';
        public int intValueS = 1;
        public long longValueS = 1;
        public float floatValueS = 1;
        public double doubleValueS = 1;

        private void readObject (ObjectInputStream in)
            throws IOException, ClassNotFoundException {
            // the default initializers will be inserted here (above the initializers provided by
            // the programmer); so though we will do some unnecessary work with our default
            // initialization, everything will still work correctly
            booleanValue = true;
            byteValue = 1;
            shortValue = 1;
            charValue = 1;
            intValue = 1;
            longValue = 1;
            floatValue = 1;
            doubleValue = 1;
            in.defaultReadObject();
        }
    }

    @Test public void testTransientPrimitives ()
    {
        Record r = new Record();
        Record nr = (Record)fromBytes(toBytes(r));

        assertEquals(r.booleanValue, nr.booleanValue);
        assertEquals(r.byteValue, nr.byteValue);
        assertEquals(r.shortValue, nr.shortValue);
        assertEquals(r.charValue, nr.charValue);
        assertEquals(r.intValue, nr.intValue);
        assertEquals(r.longValue, nr.longValue);
        assertEquals(r.floatValue, nr.floatValue, 0f);
        assertEquals(r.doubleValue, nr.doubleValue, 0.0);
    }

    @Test public void testCustomTransientPrimitives ()
    {
        CustomRecord r = new CustomRecord();
        CustomRecord nr = (CustomRecord)fromBytes(toBytes(r));

        assertEquals(r.booleanValue, nr.booleanValue);
        assertEquals(r.byteValue, nr.byteValue);
        assertEquals(r.shortValue, nr.shortValue);
        assertEquals(r.charValue, nr.charValue);
        assertEquals(r.intValue, nr.intValue);
        assertEquals(r.longValue, nr.longValue);
        assertEquals(r.floatValue, nr.floatValue, 0f);
        assertEquals(r.doubleValue, nr.doubleValue, 0.0);

        assertEquals(r.booleanValueS, nr.booleanValueS);
        assertEquals(r.byteValueS, nr.byteValueS);
        assertEquals(r.shortValueS, nr.shortValueS);
        assertEquals(r.charValueS, nr.charValueS);
        assertEquals(r.intValueS, nr.intValueS);
        assertEquals(r.longValueS, nr.longValueS);
        assertEquals(r.floatValueS, nr.floatValueS, 0f);
        assertEquals(r.doubleValueS, nr.doubleValueS, 0.0);
    }

    public static byte[] toBytes (Serializable s)
    {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(s);
            out.close();
            return bout.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object fromBytes (byte[] bytes)
    {
        try {
            ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(bytes));
            return oin.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
