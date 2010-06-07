//
// $Id$

package org.ductilej.runtime;

/**
 * Used to note checked exceptions wrapped during reflective invocation so that they can be
 * unwrapped in the appropriate places.
 */
public class WrappedException extends RuntimeException
{
    public WrappedException (Throwable cause) {
        super(cause);
    }
}
