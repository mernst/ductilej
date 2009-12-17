//
// $Id$

package org.typelessj.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.samskivert.util.LogBuilder;

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

        Class<?> clazz = object.getClass();
        StringBuilder buf = new StringBuilder("[");
        int written = 0;
        for (Field field : clazz.getFields()) {
            int mods = field.getModifiers();
            if ((mods & Modifier.STATIC) != 0) {
                continue; // we only want non-static fields
            }
            if (written > 0) {
                buf.append(", ");
            }
            buf.append(field.getName()).append("=");
            try {
                buf.append(field.get(object));
            } catch (Exception e) {
                buf.append("<error: " + e + ">");
            }
            written++;
        }
        return buf.append("]").toString();
    }
}
