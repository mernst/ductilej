//
// $Id$

package org.ductilej.tests;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of raw types.
 */
public class RawTypesTest
{
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    public void testRawTypes () {
        Map<String, String> map = new HashMap<String, String>();
        map.put("one", "two");
        Map.Entry entry1 = (Map.Entry) map.entrySet().iterator().next();
        entry1.setValue("XYZ");
        assertEquals("XYZ", map.get("one"));
    }
}
