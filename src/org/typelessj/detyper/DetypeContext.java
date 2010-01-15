//
// $Id$

package org.typelessj.detyper;

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
