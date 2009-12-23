//
// $Id$

package org.typelessj.detyper;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
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
     * Returns all methods in the supplied scope that have the specified name. You probably don't
     * want to be using this, you want {@link #resolveMethod}. This doesn't climb up the type
     * hierarchy checking parent classes or anything useful.
     */
    public List<MethodSymbol> lookupMethods (Scope scope, Name name)
    {
        return lookupAll(scope, name, MethodSymbol.class, Kinds.MTH);
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
     * Selects the closest matching method from the supplied list of overloaded methods given the
     * supplied actual argument expressions. Currently only handles arity overloading, in the
     * future the giant pile of effort will be expended to make it handle type-based overloading
     * with partial type information.
     */
    public MethodSymbol pickMethod (Env<DetypeContext> env, List<MethodSymbol> mths,
                                    List<JCExpression> args)
    {
        for (MethodSymbol mth : mths) {
            if (mth.type.asMethodType().argtypes.size() == args.size()) {
                return mth;
            }
        }
        return null;
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
     * Resolves the supplied method invocation into a symbol using information in the supplied
     * context. Performs static resolution to choose between overloaded candidates.
     */
    public MethodSymbol resolveMethod (Env<DetypeContext> env, JCMethodInvocation mexpr)
    {
        Scope scope;
        switch (mexpr.meth.getTag()) {
        case JCTree.IDENT:
            scope = env.info.scope;
            break;
        case JCTree.SELECT:
            Debug.log("Finding type of receiver", "expr", ((JCFieldAccess)mexpr.meth).selected);
            Type rtype = resolveType(env, ((JCFieldAccess)mexpr.meth).selected);
            if (rtype == null) {
                // if the selectee is not a variable in scope, maybe it's a type name
                rtype = resolveAsType(env, ((JCFieldAccess)mexpr.meth).selected);
            }
            if (rtype == null) {
                Debug.log("Can't resolve receiver type", "expr", mexpr);
                return null;
            }
            scope = ((ClassSymbol)rtype.tsym).members_field;
            break;
        default:
            Debug.log("Method not ident or select?", "expr", mexpr);
            return null;
        }

        Name mname = TreeInfo.name(mexpr.meth);
        List<MethodSymbol> mths = lookupMethods(scope, mname);
        MethodSymbol best = pickMethod(env, mths, mexpr.args);
        if (best == null) {
            Debug.log("Unable to resolve overload", "expr", mexpr, "mths", mths);
            return null;
        } else if (best.type == null) {
            Debug.log("Resolved method has no type information", "mth", best);
            return null;
        } else {
            return best;
        }
    }

    /**
     * Resolves the supplied method invocation into a symbol using information in the supplied
     * context. Performs static resolution to choose between overloaded candidates.
     */
    public MethodSymbol resolveMethodProper (Env<DetypeContext> env, JCMethodInvocation mexpr)
    {
        ClassSymbol csym;
        switch (mexpr.meth.getTag()) {
        case JCTree.IDENT:
            csym = env.enclClass.sym; // TODO: we need to create a ClassSymbol while processing
                                      // inner classes and stuff it into enclClass.sym, then clear
                                      // it out again later; then we need to be careful not to use
                                      // Types.closure()
            break;
        case JCTree.SELECT:
            Debug.log("Finding type of receiver", "expr", ((JCFieldAccess)mexpr.meth).selected);
            Type rtype = resolveType(env, ((JCFieldAccess)mexpr.meth).selected);
            if (rtype == null) {
                // if the selectee is not a variable in scope, maybe it's a type name
                rtype = resolveAsType(env, ((JCFieldAccess)mexpr.meth).selected);
            }
            if (rtype == null) {
                Debug.log("Can't resolve receiver type", "expr", mexpr);
                return null;
            }
            csym = ((ClassSymbol)rtype.tsym);
            break;
        default:
            Debug.log("Method not ident or select?", "expr", mexpr);
            return null;
        }

        // aaaaaaaaaaahhhhhhhhhhhhhhhhh! this is insanely complex, this isn't going to work either
        Name mname = TreeInfo.name(mexpr.meth);
        List<MethodSymbol> mths = List.nil();
        for (Type type : _types.closure(csym.type)) {
            Scope scope = csym.members_field;
            mths.appendList(lookupMethods(scope, mname));
        }

        MethodSymbol best = pickMethod(env, mths, mexpr.args);
        if (best == null) {
            Debug.log("Unable to resolve overload", "expr", mexpr, "mths", mths);
            return null;
        } else if (best.type == null) {
            Debug.log("Resolved method has no type information", "mth", best);
            return null;
        } else {
            return best;
        }
    }

    /**
     * Returns the symbol representing the type of the supplied expression. Currently handles bare
     * identifiers (variables), field access, and return types of method invocation.
     */
    public Type resolveType (Env<DetypeContext> env, JCExpression expr)
    {
        switch (expr.getTag()) {
        case JCTree.IDENT: {
            Name name = ((JCIdent)expr).name;
            Symbol sym = findVar(env, name);
            if (sym == null) {
                return null; // no variable in scope with that name
            }
            if (sym.type != null) {
                return sym.type; // it's got a type, let's use it!
            }

            // it may be a method formal parameter, in which case we have to dig deeper
            if (sym.owner instanceof MethodSymbol) {
                MethodSymbol msym = (MethodSymbol)sym.owner;
                Type.MethodType mtype = msym.type.asMethodType();
                int idx = 0;
                for (VarSymbol vsym : msym.params) {
                    if (vsym.name == name) {
                        return mtype.argtypes.get(idx);
                    }
                    idx++;
                }
                Debug.log("Missed formal parameter in arglist?", "expr", expr, "msym", msym);
                return null;
            }
            Debug.log("Can't resolveType() of expr", "expr", expr, "sym.owner", sym.owner);
            return null;
        }

        case JCTree.SELECT: {
            Type type = resolveType(env, ((JCFieldAccess)expr).selected);
            return (type == null) ? null : lookup(((ClassSymbol)type.tsym).members_field,
                                                  ((JCFieldAccess)expr).name, Kinds.VAR).type;
        }

        case JCTree.APPLY: {
            MethodSymbol msym = resolveMethod(env, (JCMethodInvocation)expr);
            return (msym == null) ? null : msym.type.asMethodType().restype;
        }

        default:
            Debug.log("Can't resolveType() of expr", "tag", expr.getTag(), "expr", expr);
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
    public Type resolveAsType (Env<DetypeContext> env, JCExpression expr)
    {
        // if this is a primitive type, return its predef type
        if (expr.getTag() == JCTree.TYPEIDENT) {
            return _syms.typeOfTag[((JCPrimitiveTypeTree)expr).typetag];
        }

        // maybe the whole thing names a type in scope
        Name fname = TreeInfo.fullName(expr);
        if (fname == null) {
            // TODO: LHS may be an array select expression
            Debug.log("!!! Asked to resolve as type expr with no name?", "expr", expr);
            return null;
        }
        Symbol type = findType(env, fname);
        if (type instanceof ClassSymbol) {
            // Debug.log("Found scoped type " + type);
            return ((ClassSymbol)type).type;
        }

        // maybe the selection is a class and the selectee is a member class
        if (expr.getTag() == JCTree.SELECT) {
            JCFieldAccess fa = (JCFieldAccess)expr;
            Name sname = TreeInfo.fullName(fa.selected);
            type = findType(env, sname);
            if (type instanceof ClassSymbol) {
                Symbol mtype = findMemberType(env, fa.name, (ClassSymbol)type);
                // Debug.log("Found member type " + mtype);
                return (mtype instanceof ClassSymbol) ? ((ClassSymbol)mtype).type : null;
            }
        }

        // or maybe it's just a class we've never seen before
        try {
            Debug.log("Trying load class " + fname);
            return _reader.loadClass(fname).type;
        } catch (Symbol.CompletionFailure cfe) {
            // it doesn't exist, fall through
        }

        return null;
    }

    protected Resolver (Context ctx)
    {
        ctx.put(RESOLVER_KEY, this);
        _reader = ClassReader.instance(ctx);
        _types = Types.instance(ctx);
        _syms = Symtab.instance(ctx);
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

    protected static <T extends Symbol> List<T> lookupAll (
        Scope scope, Name name, Class<T> clazz, int kind)
    {
        List<T> syms = List.nil();
        for ( ; scope != Scope.emptyScope && scope != null; scope = scope.next) {
            syms = all(scope.lookup(name), clazz, kind, syms);
        }
        return syms;
    }

    protected static <T extends Symbol> List<T> all (
        Scope.Entry e, Class<T> clazz, int kind, List<T> syms)
    {
        for ( ; e != null && e.scope != null; e = e.next()) {
            if (e.sym.kind == kind) {
                syms = syms.prepend(clazz.cast(e.sym));
            }
        }
        return syms;
    }

    protected ClassReader _reader;
    protected Types _types;
    protected Symtab _syms;

    protected static final Context.Key<Resolver> RESOLVER_KEY = new Context.Key<Resolver>();
}
