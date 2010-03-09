//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnumTest
{
    public enum TestEnum { A, B, C, D };

    // tests restoring static type in Enum constructors
    public enum StringEnum {
        A(get("A")),
        B(get("B")),
        C(get("C"));

        StringEnum (String string) {
            _string = string;
        }

        public String toString () {
            return _string;
        }

        protected String _string;
    }

    @Test public void testValueOf ()
    {
        Class<?> base_type = TestEnum.class;
        @SuppressWarnings({"unchecked","rawness","rawtypes"})
        Object eval = Enum.valueOf((Class<? extends Enum>)base_type, "B");
        assertEquals(TestEnum.B, eval);

        assertEquals("Bgot", StringEnum.B.toString());
    }

    protected static String get (String value)
    {
        return value + "got";
    }
}
