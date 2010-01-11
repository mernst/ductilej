//
// $Id$

package org.typelessj.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.typelessj.util.LogBuilder;

/**
 * Contains debugging related routines.
 */
public class Debug
{
    /**
     * Emits a debug message to stdout.
     *
     * @param args key/value pairs, (e.g. "age", someAge, "size", someSize) which will be appended
     * to the log message as [age=someAge, size=someSize].
     */
    public static void log (String message, Object... args)
    {
        System.out.println(format(message, args));
    }

    /**
     * Formats a debug message.
     *
     * @param args key/value pairs, (e.g. "age", someAge, "size", someSize) which will be appended
     * to the log message as [age=someAge, size=someSize].
     */
    public static String format (String message, Object... args)
    {
        return new LogBuilder(message, args).toString();
    }

    /**
     * Generates a string representation of the supplied object of the form <code>[field=value,
     * field=value, ...]</code> using reflection. Handy for debugging.
     */
    public static String fieldsToString (Object object)
    {
        if (object == null) {
            return "[null]";
        }

        StringBuilder buf = new StringBuilder("[");
        int written = 0;
        Class<?> clazz = object.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if ((mods & Modifier.STATIC) != 0) {
                    continue; // we only want non-static fields
                }
                if (written > 0) {
                    buf.append(", ");
                }
                buf.append(field.getName()).append("=");
                try {
                    field.setAccessible(true);
                    buf.append(field.get(object));
                } catch (Exception e) {
                    buf.append("<error: " + e + ">");
                }
                written++;
            }
            clazz = clazz.getSuperclass();
        }
        return buf.append("]").toString();
    }

    /**
     * Forcibly extracts the named field from the supplied object. For circumventing protected and
     * private access control when debugging.
     */
    public static Object get (Object obj, String field)
    {
        Field f = getField(obj.getClass(), field);
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get " + field + " of " + obj, e);
        }
    }

    public static Field getField (Class<?> clazz, String fname)
    {
        if (clazz == null) {
            return null;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(fname)) {
                return field;
            }
        }
        return getField(clazz.getSuperclass(), fname);
    }
}
