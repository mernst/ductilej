//
// $Id$

package org.typelessj.util;

import javax.lang.model.type.TypeKind;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.*;

/**
 * AST related utility methods.
 */
public class ASTUtil
{
    /**
     * Returns true if s is a method symbol that overrides a method in a superclass. Stolen from
     * Check.java.
     */
    public static boolean isOverrider (Types types, Symbol s)
    {
        if (s == null || s.kind != Kinds.MTH || s.isStatic()) {
            return false;
        }
        Symbol.MethodSymbol m = (Symbol.MethodSymbol)s;
        Symbol.TypeSymbol owner = (Symbol.TypeSymbol)m.owner;
        for (Type sup : types.closure(owner.type)) {
            if (sup == owner.type) {
                continue; // skip "this"
            }
            Scope scope = sup.tsym.members();
            for (Scope.Entry e = scope.lookup(m.name); e.scope != null; e = e.next()) {
                if (!e.sym.isStatic() && m.overrides(e.sym, owner, types, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the supplied symbol belongs to a class that is not being transformed (a
     * library class).
     */
    public static boolean isLibrary (Symbol sym)
    {
        if (sym == null) {
            return false;
        }
        if (!(sym instanceof Symbol.ClassSymbol)) {
            return isLibrary(sym.outermostClass());
        }
        Symbol.ClassSymbol csym = (Symbol.ClassSymbol)sym;
        if (csym.fullname == null) { // we're an anonymous inner class
            return isLibrary(sym.outermostClass());
        }
        // TODO: we need to annotate classes that have been transformed in a way that we can detect
        // that information in the Type or TypeSymbol for the class (a class annotation?)
        return csym.fullname.toString().startsWith("java.");
    }

    /**
     * Returns true if the supplied expression represents the void type (only shows up as the
     * declared return type for a method).
     */
    public static boolean isVoid (JCExpression expr)
    {
        return (expr instanceof JCPrimitiveTypeTree) &&
            ((JCPrimitiveTypeTree)expr).getPrimitiveTypeKind() == TypeKind.VOID;
    }

    /**
     * Returns an expanded string representation of the supplied symbol. Used for debugging.
     */
    public static String expand (Symbol sym)
    {
        if (sym instanceof Symbol.ClassSymbol) {
            Symbol.ClassSymbol csym = (Symbol.ClassSymbol)sym;
            return csym.fullname + "(t=" + csym.type + ", " + csym.members_field + ")";
        } else {
            return String.valueOf(sym);
        }
    }

    public static String extype (Types types, Type type)
    {
        Type stype = types.supertype(type);
        return type + "/" + type.tsym + "/" + isLibrary(type.tsym) +
            ((stype == null) ? "" : (" <- " + extype(types, stype)));
    }
}
