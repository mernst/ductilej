//
// $Id$

package org.ductilej.util;

import java.util.Enumeration;
import java.util.Iterator;

/**
 * Formats a message and an array of alternating key value pairs like so:
 *
 * <pre>message [key=value, key=value, key=value]</pre>
 */
public class LogBuilder
{
    /**
     * Generates a string representation of the supplied value. Is moderately smart about
     * generating strings for arrays, iterables and iterators.
     */
    public static void toString (StringBuilder buf, Object val)
    {
        if (val instanceof byte[]) {
            buf.append(OPEN_BOX);
            byte[] v = (byte[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof short[]) {
            buf.append(OPEN_BOX);
            short[] v = (short[])val;
            for (short i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof int[]) {
            buf.append(OPEN_BOX);
            int[] v = (int[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof long[]) {
            buf.append(OPEN_BOX);
            long[] v = (long[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof float[]) {
            buf.append(OPEN_BOX);
            float[] v = (float[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof double[]) {
            buf.append(OPEN_BOX);
            double[] v = (double[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof Object[]) {
            buf.append(OPEN_BOX);
            Object[] v = (Object[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                toString(buf, v[i]);
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof boolean[]) {
            buf.append(OPEN_BOX);
            boolean[] v = (boolean[])val;
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                buf.append(v[i] ? "t" : "f");
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof Iterable<?>) {
            toString(buf, ((Iterable<?>)val).iterator());

        } else if (val instanceof Iterator<?>) {
            buf.append(OPEN_BOX);
            Iterator<?> iter = (Iterator<?>)val;
            for (int i = 0; iter.hasNext(); i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                toString(buf, iter.next());
            }
            buf.append(CLOSE_BOX);

        } else if (val instanceof Enumeration<?>) {
            buf.append(OPEN_BOX);
            Enumeration<?> enm = (Enumeration<?>)val;
            for (int i = 0; enm.hasMoreElements(); i++) {
                if (i > 0) {
                    buf.append(SEP);
                }
                toString(buf, enm.nextElement());
            }
            buf.append(CLOSE_BOX);

        } else {
            buf.append(val);
        }
    }

    /**
     * Creates a log builder with no message and no initial key value pairs.
     */
    public LogBuilder ()
    {
        this("");
    }

    /**
     * Creates a log builder with the given message and key value pairs.
     */
    public LogBuilder (Object message, Object... args)
    {
        _log = new StringBuilder().append(message);
        append(args);
    }

    /**
     * Adds the given key value pairs to the log.
     */
    public LogBuilder append (Object... args)
    {
        if (args != null && args.length > 1) {
            for (int ii = 0, nn = args.length / 2; ii < nn; ii++) {
                if (_hasArgs) {
                    _log.append(", ");
                } else {
                    if (_log.length() > 0) {
                        // only need a space if we have a message
                        _log.append(' ');
                    }
                    _log.append('[');
                    _hasArgs = true;
                }
                _log.append(args[2 * ii]).append("=");
                try {
                    toString(_log, args[2 * ii + 1]);
                } catch (Throwable t) {
                    _log.append("<toString() failure: " + t + ">");
                }
            }
        }
        return this;
    }

    /**
     * Returns the formatted log message. Does not reset the buffer. You can continue to append
     * arguments and call {@link #toString} again.
     */
    @Override public String toString ()
    {
        String log = _log.toString();
        if (_hasArgs) {
            log += "]";
        }
        return log;
    }

    protected boolean _hasArgs;
    protected StringBuilder _log;

    protected static final String OPEN_BOX = "(";
    protected static final String CLOSE_BOX = ")";
    protected static final String SEP = ", ";
}
