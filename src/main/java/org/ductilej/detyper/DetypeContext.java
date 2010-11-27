//
// $Id$

package org.ductilej.detyper;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree.JCExpression;

/**
 * Used to compute scopes during the detyping process.
 */
public class DetypeContext
{
    /** The symbols in scope in this context. */ 
    public Scope scope = null;

    /** The parent of the anonymous inner class currently being created, if any. */
    public Symbol anonParent;

    /** The type of the current array initializer element type, if any. */
    public JCExpression arrayElemType;

    /** True if we're in the middle of processing a call to this() or super(). */
    public boolean inChainedCons;

    /** A record of the lint/SuppressWarnings currently in effect. */
    public Lint lint;

    /**
     * Duplicates this context with the specified new scope.
     */
    public DetypeContext dup (Scope scope)
    {
        DetypeContext ctx = new DetypeContext();
        ctx.scope = scope;
        ctx.anonParent = anonParent;
        ctx.arrayElemType = arrayElemType;
        ctx.inChainedCons = inChainedCons;
        return ctx;
    }

    /**
     * Duplicates this context with the same scope.
     */
    public DetypeContext dup ()
    {
        return dup(this.scope);
    }
}
