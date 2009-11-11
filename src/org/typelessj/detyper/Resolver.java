//
// $Id$

package org.typelessj.detyper;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.code.Type;

import org.typelessj.runtime.RT;

/**
 * Handles some simple name resolution tasks.
 */
public class Resolver
{
    /**
     * Locates the closest variable symbol in the supplied context with the specified name.
     * Returns null if no match is found.
     */
    public static Symbol findVar (Env<DetypeContext> env, Name name)
    {
        return find(env, name, Kinds.VAR);
    }

    /**
     * Locates the closest method symbol in the supplied context with the specified name.  Returns
     * null if no match is found.
     */
    public static Symbol findMethod (Env<DetypeContext> env, Name name)
    {
        return find(env, name, Kinds.MTH);
    }

    /**
     * Locates the closest type symbol in the supplied context with the specified name.  Returns
     * null if no match is found.
     */
    public static Symbol findType (Env<DetypeContext> env, Name name)
    {
        for (Env<DetypeContext> env1 = env; env1.outer != null; env1 = env1.outer) {
            Symbol sym = lookup(env1.info.scope, name, Kinds.TYP);
            if (sym != null) {
                return sym;
            }
            if (env1.enclClass.sym == null) {
                System.err.println("TODO: can't findType in inner class."); // TODO
                continue;
            }
            sym = findMemberType(env, name, env1.enclClass.sym);
            if (sym != null) {
                return sym;
            }
        }

        // then we have to check named imports
        Symbol sym = lookup(env.toplevel.namedImportScope, name, Kinds.TYP);
        if (sym != null) {
            return sym;
        }

        // finally we check star imports
        sym = lookup(env.toplevel.starImportScope, name, Kinds.TYP);
        if (sym != null) {
            return sym;
        }

//         if (env.tree.getTag() != JCTree.IMPORT) {
//             sym = findGlobalType(env, env.toplevel.namedImportScope, name);
//             if (sym.exists()) return sym;
//             else if (sym.kind < bestSoFar.kind) bestSoFar = sym;

//             sym = findGlobalType(env, env.toplevel.packge.members(), name);
//             if (sym.exists()) return sym;
//             else if (sym.kind < bestSoFar.kind) bestSoFar = sym;

//             sym = findGlobalType(env, env.toplevel.starImportScope, name);
//             if (sym.exists()) return sym;
//             else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
//         }

        return null;
    }

    /**
     * Returns the type of the supplied expression as a string, like "int" or "String" or
     * "foo.bar.Baz".
     */
    public static String resolveType (Env<DetypeContext> env, JCExpression expr)
    {
        if (expr instanceof JCIdent) {
            Name name = ((JCIdent)expr).name;
            Symbol sym = findVar(env, name);
            if (sym.type != null) {
                if (!(sym.type instanceof Type.ClassType)) {
                    RT.debug("Aiya, have funny type", "expr", expr, "sym", sym, "type", sym.type);
                    return null;
                }
                RT.debug("Found type, now what?", "expr", expr, "sym", sym, "type", sym.type);
                return null;

            } else {
                if (sym.owner instanceof MethodSymbol) {
                    MethodSymbol msym = (MethodSymbol)sym.owner;
                    Type.MethodType mtype = msym.type.asMethodType();
                    int idx = 0;
                    for (VarSymbol vsym : msym.params) {
                        if (vsym.name == name) {
                            // hack: rely on the Type's toString() method to do what we want
                            return ""+mtype.argtypes.get(idx);
                        }
                        idx++;
                    }
                    RT.debug("Missed formal parameter in arglist?", "expr", expr, "msym", msym);
                    return null;

                } else {
                    RT.debug("Is not formal parameter", "expr", expr, "sym.owner", sym.owner);
                    return null;
                }
            }

        } else {
            RT.debug("Can't handle non-idents", "expr", expr);
            return null;
        }
    }

    protected static Symbol findMemberType (Env<DetypeContext> env, Name name, TypeSymbol c)
    {
        // RT.debug("Checking for " + name + " as member of " + c + " " + c.members());
        Symbol sym = first(c.members().lookup(name), Kinds.TYP);
        if (sym != null) {
            return sym;
        }

        return null; // TODO: scan supertypes and interfaces

//         Type st = types.supertype(c.type);
//         if (st != null && st.tag == CLASS) {
//             sym = findMemberType(env, site, name, st.tsym);
//             if (sym.kind < bestSoFar.kind) bestSoFar = sym;
//         }
//         for (List<Type> l = types.interfaces(c.type);
//              bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
//              l = l.tail) {
//             sym = findMemberType(env, site, name, l.head.tsym);
//             if (bestSoFar.kind < AMBIGUOUS && sym.kind < AMBIGUOUS &&
//                 sym.owner != bestSoFar.owner)
//                 bestSoFar = new AmbiguityError(bestSoFar, sym);
//             else if (sym.kind < bestSoFar.kind)
//                 bestSoFar = sym;
//         }
    }

    protected static Symbol find (Env<DetypeContext> env, Name name, int kind)
    {
        if (name == null) {
            RT.debug("Asked to lookup null name...", new Throwable());
            return null;
        }

        // first we check our local environment
        for ( ; env.outer != null; env = env.outer) {
            // RT.debug("Lookup", "name", name, "kind", kind, "scope", env.info.scope);
            Symbol sym = lookup(env.info.scope, name, kind);
            if (sym != null) {
                return sym;
            }
        }

        // then we have to check named imports
        Symbol sym = lookup(env.toplevel.namedImportScope, name, kind);
        if (sym != null) {
            return sym;
        }

        // finally we check star imports
        sym = lookup(env.toplevel.starImportScope, name, kind);
        if (sym != null) {
            return sym;
        }

        return null;
    }

    protected static Symbol lookup (Scope scope, Name name, int kind)
    {
        for ( ; scope != Scope.emptyScope && scope != null; scope = scope.next) {
            Symbol sym = first(scope.lookup(name), kind);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    protected static Symbol first (Scope.Entry e, int kind)
    {
        for ( ;e != null && e.scope != null; e = e.next()) {
            if (e.sym.kind == kind) {
                return e.sym;
            }
        }
        return null;
    }
}
