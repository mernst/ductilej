//
// $Id$

package org.ductilej.runtime;

/**
 * Thrown when two or more methods match the arguments provided to a runtime method call that was
 * not resolvable at compile time.
 */
public class AmbiguousMethodError extends RuntimeException
{
    public AmbiguousMethodError (String error)
    {
        super(error);
    }
}
