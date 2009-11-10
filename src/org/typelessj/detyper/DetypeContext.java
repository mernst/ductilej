//
// $Id$

package org.typelessj.detyper;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;

/**
 * Used to compute scopes during the detyping process.
 */
public class DetypeContext
{
    /** The symbols in scope in this context. */ 
    public Scope scope = null;

    /** The parent of the anonymous inner class currently being created, if any. */
    public Symbol anonParent;

    /**
     * Duplicates this context with the specified new scope.
     */
    public DetypeContext dup (Scope scope)
    {
        DetypeContext ctx = new DetypeContext();
        ctx.scope = scope;
        ctx.anonParent = anonParent;
        return ctx;
    }
}
