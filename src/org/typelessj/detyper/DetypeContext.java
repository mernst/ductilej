//
// $Id$

package org.typelessj.detyper;

import com.sun.tools.javac.code.Scope;

/**
 * Used to compute scopes during the detyping process.
 */
public class DetypeContext
{
    /** The symbols in scope in this context. */ 
    public Scope scope = null;

    /**
     * Duplicates this context with the specified new scope.
     */
    public DetypeContext dup (Scope scope)
    {
        DetypeContext ctx = new DetypeContext();
        ctx.scope = scope;
        return ctx;
    }
}
