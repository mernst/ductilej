//
// $Id$

package org.ductilej.tests;

import java.lang.reflect.Method;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the wrapping and unwrapping of arguments that takes place when we issue reflective calls
 * through yet another layer of reflection.
 */
public class ReflectiveInvokeTest
{
    @Test public void testReflectiveInvoke () throws Exception {
        int ran = 0;
        for (Method m : ReflectiveInvokeTest.class.getDeclaredMethods()) {
            if (m.getName().startsWith("testMethod")) {
                Object[] args = new Object[m.getParameterTypes().length];
                assertEquals("ran", invoke(m, null, args));
                ran += 1;
            }
        }
        assertEquals(1, ran);
    }

    protected static Object invoke (Method m, Object instance, Object[] params) throws Exception {
        m.setAccessible(true);
        return m.invoke(instance, params);
    }

    protected static String testMethod (String arg) {
        return "ran";
    }
}
