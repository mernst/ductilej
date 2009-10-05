//
// $Id$

package org.typelessj.util;

import javax.lang.model.type.TypeKind;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;

import org.typelessj.runtime.Transformed;

/**
 * AST related utility methods.
 */
public class ASTUtil
{
    /**
     * Returns true if s is a method symbol that overrides a method in an untransformed superclass.
     * Adapted from javac's Check class.
     */
    public static boolean isLibraryOverrider (Types types, Symbol s)
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
                // if we override a library method, we're an overrider
                if (!e.sym.isStatic() && m.overrides(e.sym, owner, types, true) &&
                    isLibrary(e.sym)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the supplied symbol belongs to a class that has not been transformed (a
     * library class).
     */
    public static boolean isLibrary (Symbol sym)
    {
        if (sym == null) {
            return false; // TODO: under what circumstances are we supplied with a null symbol?
        }
        if (!(sym instanceof Symbol.ClassSymbol)) {
            // TODO: this is assuming that if the containing class of an inner class is
            // transformed, then every inner class therein is transformed; this may or may not be
            // an acceptable assumption, I need to think about it
            return isLibrary(sym.outermostClass());
        }
        Symbol.ClassSymbol csym = (Symbol.ClassSymbol)sym;
        if (csym.fullname == null) { // we're an anonymous inner class
            return isLibrary(sym.outermostClass());
        }

        // check whether the class that defines this symbol has the @Transformed annotation
        for (List<Attribute.Compound> attrs = sym.attributes_field;
             !attrs.isEmpty(); attrs = attrs.tail) {
            if (Transformed.class.getName().equals(attrs.head.getAnnotationType().toString())) {
                return false;
            }
        }
        return true;
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
