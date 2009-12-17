//
// $Id$

package org.typelessj.detyper;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import org.typelessj.runtime.Debug;

/**
 * Handles some simple name resolution tasks.
 */
public class Resolver
{
    /**
     * Returns our simple symbol resolver.
     */
    public static Resolver instance (Context context)
    {
        Resolver instance = context.get(RESOLVER_KEY);
        if (instance == null) {
            instance = new Resolver(context);
        }
        return instance;
    }

    /**
     * Locates the closest variable symbol in the supplied context with the specified name.
     * Returns null if no match is found.
     */
    public Symbol findVar (Env<DetypeContext> env, Name name)
    {
        return find(env, name, Kinds.VAR);
    }

    /**
     * Locates the closest method symbol in the supplied context with the specified name.  Returns
     * null if no match is found.
     */
    public Symbol findMethod (Env<DetypeContext> env, Name name)
    {
        return find(env, name, Kinds.MTH);
    }

    /**
     * Returns all methods in the supplied scope that have the specified name.
     */
    public List<Symbol> findMethods (Scope scope, Name name)
    {
        return lookupAll(scope, name, Kinds.MTH);
    }

    /**
     * Locates the closest type symbol in the supplied context with the specified name.  Returns
     * null if no match is found.
     */
    public Symbol findType (Env<DetypeContext> env, Name name)
    {
        for (Env<DetypeContext> env1 = env; env1.outer != null; env1 = env1.outer) {
            Symbol sym = lookup(env1.info.scope, name, Kinds.TYP);
            if (sym != null) {
                return sym;
            }
            if (env1.enclClass.sym == null) {
                Debug.log("TODO: can't findType in inner class", "name", name); // TODO
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

        // then we check package members (for unqualified references to types in our package)
        sym = lookup(env.toplevel.packge.members(), name, Kinds.TYP);
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
    public String resolveType (Env<DetypeContext> env, JCExpression expr)
    {
        if (expr instanceof JCIdent) {
            Name name = ((JCIdent)expr).name;
            Symbol sym = findVar(env, name);
            if (sym.type != null) {
                if (!(sym.type instanceof Type.ClassType)) {
                    Debug.log("Aiya, have funny type", "expr", expr, "sym", sym, "type", sym.type);
                    return null;
                }
                Debug.log("Found type, now what?", "expr", expr, "sym", sym, "type", sym.type);
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
                    Debug.log("Missed formal parameter in arglist?", "expr", expr, "msym", msym);
                    return null;

                } else {
                    Debug.log("Is not formal parameter", "expr", expr, "sym.owner", sym.owner);
                    return null;
                }
            }

        } else {
            Debug.log("Can't handle non-idents", "expr", expr);
            return null;
        }
    }

    /**
     * Resolves the supplied expression as the name of a type (e.g. "foo.bar.Baz", "Baz",
     * "Baz.Bif", "foo.bar.Baz.Bif"). Returns the referenced type or null if expression does not
     * name a type.
     *
     * <p> Note: this may or may not do anything sensible if used on type variables. Don't do that.
     */
    public ClassSymbol resolveAsType (Env<DetypeContext> env, JCExpression expr)
    {
        // maybe the whole thing names a type in scope
        Name fname = TreeInfo.fullName(expr);
        Symbol type = findType(env, fname);
        if (type instanceof ClassSymbol) {
            Debug.log("Found scoped type " + type);
            return (ClassSymbol)type;
        }

        // maybe the selection is a class and the selectee is a member class
        if (expr instanceof JCFieldAccess) {
            JCFieldAccess fa = (JCFieldAccess)expr;
            Name sname = TreeInfo.fullName(fa.selected);
            type = findType(env, sname);
            if (type instanceof ClassSymbol) {
                Symbol mtype = findMemberType(env, fa.name, (ClassSymbol)type);
                Debug.log("Found member type " + mtype);
                return (mtype instanceof ClassSymbol) ? (ClassSymbol)mtype : null;
            }
        }

        // or maybe it's just a class we've never seen before
        try {
            Debug.log("Trying load class " + fname);
            return _reader.loadClass(fname);
        } catch (Symbol.CompletionFailure cfe) {
            // it doesn't exist, fall through
        }

        return null;
    }

    protected Resolver (Context ctx)
    {
        ctx.put(RESOLVER_KEY, this);
        _reader = ClassReader.instance(ctx);
    }

    protected Symbol findMemberType (Env<DetypeContext> env, Name name, TypeSymbol c)
    {
        // Debug.log("Checking for " + name + " as member of " + c + " " + c.members());
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

    protected Symbol find (Env<DetypeContext> env, Name name, int kind)
    {
        if (name == null) {
            Debug.log("Asked to lookup null name...", new Throwable());
            return null;
        }

        // first we check our local environment
        for ( ; env.outer != null; env = env.outer) {
            // Debug.log("Lookup", "name", name, "kind", kind, "scope", env.info.scope);
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
        for ( ; e != null && e.scope != null; e = e.next()) {
            if (e.sym.kind == kind) {
                return e.sym;
            }
        }
        return null;
    }

    protected static List<Symbol> lookupAll (Scope scope, Name name, int kind)
    {
        List<Symbol> syms = List.nil();
        for ( ; scope != Scope.emptyScope && scope != null; scope = scope.next) {
            syms = all(scope.lookup(name), kind, syms);
        }
        return syms;
    }

    protected static List<Symbol> all (Scope.Entry e, int kind, List<Symbol> syms)
    {
        Debug.log("Looking for all " + kind + " in " + Debug.fieldsToString(e));
        for ( ; e != null && e.scope != null; e = e.next()) {
            Debug.log("Is match? " + e.sym + "? " + e.sym.kind);
            if (e.sym.kind == kind) {
                syms = syms.prepend(e.sym);
            }
        }
        return syms;
    }

    protected ClassReader _reader;

    protected static final Context.Key<Resolver> RESOLVER_KEY = new Context.Key<Resolver>();
}
