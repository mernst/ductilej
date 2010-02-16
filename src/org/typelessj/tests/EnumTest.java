//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnumTest
{
    public enum TestEnum { A, B, C, D };

    @Test public void testValueOf ()
    {
        Class<?> base_type = TestEnum.class;
        @SuppressWarnings({"unchecked","rawness","rawtypes"})
        Object eval = Enum.valueOf ((Class<? extends Enum>)base_type, "B");
        assertEquals(TestEnum.B, eval);
    }
}
