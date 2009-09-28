//
// $Id$

package org.typelessj.util;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;

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
        if (s.kind != Kinds.MTH || s.isStatic()) {
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
}
