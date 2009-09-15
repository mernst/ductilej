//
// $Id$

package org.typelessj.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Provides dynamic method dispatch, operator evaluation and other bits.
 */
public class RT
{
    /**
     * Invokes the specified method via reflection, performing runtime type resolution and handling
     * the necessary name mangling to cope with de-typed overloads.
     */
    public static Object invoke (Object receiver, String mname, Object... args)
    {
        if (receiver == null) {
            throw new NullPointerException();
        }

        Method method = findMethod(receiver.getClass(), mname, args);
        if (method == null) {
            throw new NoSuchMethodError(); // TODO
        }

        try {
            return method.invoke(receiver, args);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite);
        }
    }

    /**
     * A helper for {@link #invoke}.
     */
    protected static Method findMethod (Class clazz, String mname, Object... args)
    {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(mname)) {
                return method;
            }
        }
        Class parent = clazz.getSuperclass();
        return (parent == null) ? null : findMethod(parent, mname, args);
    }
}
