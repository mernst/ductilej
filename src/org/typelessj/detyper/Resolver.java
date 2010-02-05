//
// $Id$

package org.typelessj.detyper;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.FatalError;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

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
            if (mth.type.asMethodType().argtypes.size() == args.size() || mth.isVarArgs()) {
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
        // TODO: handle type variables; not yet sure how that is appropriately done

        for (Env<DetypeContext> env1 = env; env1.outer != null; env1 = env1.outer) {
            Symbol sym = lookup(env1.info.scope, name, Kinds.TYP);
            if (sym != null) {
                return sym;
            }
            if (env1.enclClass.sym == null) {
                Debug.warn("Can't findType in inner class", "name", name); // TODO
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
    public Symbol resolveMethod (Env<DetypeContext> env, JCMethodInvocation mexpr)
    {
        Name mname = TreeInfo.name(mexpr.meth);

        switch (mexpr.meth.getTag()) {
        case JCTree.IDENT: {
            Symbol sym;
            List<Type> tatypes = resolveTypes(env, mexpr.typeargs, Kinds.TYP);
            List<Type> atypes = resolveRawTypes(env, mexpr.args, Kinds.VAL);
            // pass the buck to javac's Resolve to do the heavy lifting
            if (mname == _names._this || mname == _names._super) {
                Type site = env.enclClass.sym.type;
                if (mname == _names._super) {
                    if (site == _syms.objectType) {
                        site = _types.createErrorType(_syms.objectType);
                    } else {
                        site = _types.supertype(site);
                    }
                }
                // Debug.log("Resolving " + mname + "<" + tatypes + ">(" + atypes + ")");
                sym = invoke(env, Backdoor.resolveConstructor, _resolve, mexpr.pos(),
                             Detype.toAttrEnv(env), site, atypes, tatypes);
            } else {
                // Debug.log("Resolving " + mname + "<" + tatypes + ">(" + atypes + ")");
                sym = invoke(env, Backdoor.resolveMethod, _resolve, mexpr.pos(),
                             Detype.toAttrEnv(env), mname, atypes, tatypes);
            }
            if (sym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve method", "expr", mexpr);
            }
            // Debug.log("Asked javac to resolve method " + mexpr + " got " + sym);
            return sym;
        }

        case JCTree.SELECT: {
            // we erase the type parameters from the site because we want javac to ignore the type
            // arguments when resolving our method (to be maximally lenient)
            // Debug.log("Resolving method receiver", "expr", mexpr);
            Type site = resolveRawType(
                env, ((JCFieldAccess)mexpr.meth).selected, Kinds.VAL | Kinds.TYP);
            if (site == null) {
                Debug.warn("Can't resolve receiver type", "expr", mexpr);
                return null;
            }
            // Debug.log("Resolved method receiver", "expr", mexpr, "site", site);

            // pass the buck to javac's Resolve to do the heavy lifting
            List<Type> tatypes = resolveTypes(env, mexpr.typeargs, Kinds.TYP);
            List<Type> atypes = resolveRawTypes(env, mexpr.args, Kinds.VAL);
            // Debug.log("Resolving {" + site + "}." + mname + "<" + tatypes + ">(" + atypes + ")");
            Symbol sym = invoke(env, Backdoor.resolveQualifiedMethod, _resolve, mexpr.pos(),
                                Detype.toAttrEnv(env), site, mname, atypes, tatypes);
            if (sym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve method", "expr", mexpr, "site", site);
            }
            // Debug.log("Asked javac to resolve method " + mexpr + " got " + sym);
            return sym;
        }

        default:
            Debug.log("Method not ident or select?", "expr", mexpr);
            return null;
        }
    }

    /**
     * Resolves the types of all expressions in the supplied list.
     */
    public List<Type> resolveRawTypes (Env<DetypeContext> env, List<JCExpression> exprs, int pkind)
    {
        return exprs.isEmpty() ? List.<Type>nil() :
            resolveRawTypes(env, exprs.tail, pkind).prepend(resolveRawType(env, exprs.head, pkind));
    }

    /**
     * Returns the erased type of the supplied expression.
     */
    public Type resolveRawType (Env<DetypeContext> env, JCTree expr, int pkind)
    {
        Type type = resolveType(env, expr, pkind);
        return (type == null) ? null : _types.erasure(type);
    }

    /**
     * Resolves the types of all expressions in the supplied list.
     */
    public List<Type> resolveTypes (Env<DetypeContext> env, List<JCExpression> exprs, int pkind)
    {
        return exprs.isEmpty() ? List.<Type>nil() :
            resolveTypes(env, exprs.tail, pkind).prepend(resolveType(env, exprs.head, pkind));
    }

    /**
     * Returns the (possibly parameterized) type of the supplied expression.
     */
    public Type resolveType (Env<DetypeContext> env, JCTree expr, int pkind)
    {
        // we already have a resolved symbol and type, just use that
        Symbol sym = TreeInfo.symbol(expr);
        if (sym != null) {
            return sym.type;
        }

        // Debug.log("Resolving type", "expr", expr, "pkind", pkind);
        switch (expr.getTag()) {
        case JCTree.IDENT: {
            Name name = TreeInfo.name(expr);
            if (name == _names._this) {
                sym = env.enclClass.sym;
//             } else if (name == _names._super) {
//                 Type stype = _types.supertype(env.enclClass.sym.type);
//                 if (stype.tag == TypeTags.CLASS) {
//                     sym = (ClassSymbol)stype.tsym;
//                 } else {
//                     Debug.warn("Class has non-class 'super'?", "csym", env.enclClass.sym,
//                                "stype", stype);
//                     return null;
//                 }
            } else {
                // Debug.log("Resoving ident", "name", name, "pkind", pkind);
                sym = invoke(env, Backdoor.resolveIdent, _resolve,
                             expr.pos(), Detype.toAttrEnv(env), name, pkind);
            }
            if (sym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve type of ident", "expr", expr, "sym", sym);
            }
            return sym.type; // sym.erasure(_types);
        }

        case JCTree.SELECT: {
            JCFieldAccess facc = (JCFieldAccess)expr;
//             // if this is a ClassName.class expression, it must be handled specially
//             if (facc.name == _names._class) {
//                 Type site = resolveAsType(env, facc.selected, false); // TODO: maybe assumeObject?
//                 // we need to supply the correct type parameter for Class<T>
//                 return new Type.ClassType(_syms.classType.getEnclosingType(),
//                                           List.of(_types.erasure(site)), _syms.classType.tsym);
//             }
//             // TODO: if LHS is an array and we're selecting length, we need to handle that
//             // specially as well

            // determine the expected kind of the qualifier expression
            int skind = 0;
            if (facc.name == _names._this || facc.name == _names._super ||
                facc.name == _names._class) {
                skind = Kinds.TYP;
            } else {
                if ((pkind & Kinds.PCK) != 0) skind = skind | Kinds.PCK;
                if ((pkind & Kinds.TYP) != 0) skind = skind | Kinds.TYP | Kinds.PCK;
                if ((pkind & (Kinds.VAL | Kinds.MTH)) != 0) skind = skind | Kinds.VAL | Kinds.TYP;
            }

            // otherwise this should be the selection of a field from an object
            Type site = resolveType(env, facc.selected, skind);
            if (site == null) {
                Debug.warn("Unable to resolve receiver of field select: " + expr);
                return null;
            }

            sym = invoke(env, Backdoor.selectSym, _attr, facc, site, Detype.toAttrEnv(env),
                         Type.noType, pkind);
            if (sym == null) {
                Debug.warn("Unable to resolve symbol for field select", "expr", expr, "site", site);
                return null;
            }
            return sym.type; // sym.erasure(_types);
        }

        case JCTree.APPLY:
            sym = resolveMethod(env, (JCMethodInvocation)expr);
            return (sym == null) ? null : sym.type.asMethodType().restype;

        case JCTree.NEWCLASS:
            // TODO: this isn't quite right since it doesn't return the correct symbol for
            // anonymous inner classes...
            return resolveType(env, ((JCNewClass)expr).clazz, Kinds.TYP);

        case JCTree.TYPECAST:
            return resolveType(env, ((JCTypeCast)expr).clazz, Kinds.TYP);

        case JCTree.PARENS:
            return resolveType(env, ((JCParens)expr).expr, pkind);

        case JCTree.INDEXED: {
            Type atype = resolveType(env, ((JCArrayAccess)expr).indexed, pkind);
            if (atype instanceof Type.ArrayType) {
                return ((Type.ArrayType)atype).elemtype;
            } else {
                Debug.warn("Can't resolveType() of array index expr", "expr", expr, "atype", atype);
                return null;
            }
        }

        case JCTree.CONDEXPR:
            // obtain the type of a ?: expr from the true part (could cause confusion if the types
            // don't match, but should work for our purposes)
            return resolveType(env, ((JCConditional)expr).truepart, pkind);

        case JCTree.OR: // ||
        case JCTree.AND: // &&
        case JCTree.EQ: // ==
        case JCTree.NE: // !=
        case JCTree.LT: // <
        case JCTree.GT: // >
        case JCTree.LE: // <=
        case JCTree.GE: // >=
            return _syms.typeOfTag[TypeTags.BOOLEAN];

        case JCTree.BITOR: // |
        case JCTree.BITXOR: // ^
        case JCTree.BITAND: // &
        case JCTree.SL: // <<
        case JCTree.SR: // >>
        case JCTree.USR: // >>>
        case JCTree.MINUS: // -
        case JCTree.MUL: // *
        case JCTree.DIV: // /
        case JCTree.MOD: // %
            return _syms.typeOfTag[TypeTags.INT]; // TODO: is this true?

        case JCTree.POS: // +
        case JCTree.NEG: // -
        case JCTree.NOT: // !
        case JCTree.COMPL: // ~
        case JCTree.PREINC: // ++ _
        case JCTree.PREDEC: // -- _
        case JCTree.POSTINC: // _ ++
        case JCTree.POSTDEC: // _ --
            return _syms.typeOfTag[TypeTags.INT]; // TODO: is this true?

        case JCTree.PLUS: // +
            // if lhs is string, then expr is string, otherwise expr is int
            if (resolveType(env, ((JCBinary)expr).lhs, pkind) == _syms.stringType) {
                return _syms.stringType;
            } else {
                return _syms.typeOfTag[TypeTags.INT];
            }

        case JCTree.LITERAL: {
            int tag = ((JCLiteral)expr).typetag;
            // TODO: are there other literals that don't have direct type tag mapping?
            return (tag == TypeTags.CLASS) ? _syms.stringType : _syms.typeOfTag[tag];
        }

        case JCTree.TYPEIDENT:
            return _syms.typeOfTag[((JCPrimitiveTypeTree)expr).typetag];

        case JCTree.TYPEARRAY: {
            Type etype = resolveType(env, ((JCArrayTypeTree)expr).elemtype, Kinds.TYP);
            return (etype == null) ? null : new Type.ArrayType(etype, etype.tsym);
        }

        case JCTree.TYPEAPPLY:
            // we ignore the type arguments and recurse down to the base type
            return resolveType(env, ((JCTypeApply)expr).clazz, Kinds.TYP);

        default:
            Debug.warn("Can't resolveType() of expr", "tag", expr.getTag(), "expr", expr,
                       "etype", expr.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Returns true if the supplied expression resolves to a class.
     */
    public boolean isStaticSite (Env<DetypeContext> env, JCExpression expr)
    {
        // Debug.log("isStaticReceiver(" + fa + ")");

        switch (expr.getTag()) {
        case JCTree.IDENT: {
            Name name = TreeInfo.name(expr);
            if (name == _names._this || name == _names._super) {
                return false;
            } else {
                Symbol sym = invoke(env, Backdoor.findIdent, _resolve, Detype.toAttrEnv(env),
                                    name, Kinds.PCK | Kinds.TYP | Kinds.VAL);
                return (sym != null) && (sym.kind == Kinds.TYP);
            }
        }

        case JCTree.SELECT: {
            JCFieldAccess facc = (JCFieldAccess)expr;
            if (facc.name == _names._this || facc.name == _names._super ||
                facc.name == _names._class) {
                return false;
            }
            int skind = Kinds.PCK | Kinds.TYP | Kinds.VAL;
            Type site = resolveType(env, facc.selected, skind);
            if (site == null) {
                Debug.warn("Unable to resolve receiver of field select: " + expr);
                return false;
            }
            Symbol sym = invoke(env, Backdoor.selectSym, _attr, facc, site, Detype.toAttrEnv(env),
                                Type.noType, skind);
            return (sym != null) && (sym.kind == Kinds.TYP);
        }

        default:
            return false;
        }
    }

    protected Resolver (Context ctx)
    {
        ctx.put(RESOLVER_KEY, this);
        _reader = ClassReader.instance(ctx);
        _types = Types.instance(ctx);
        _syms = Symtab.instance(ctx);
        _names = Names.instance(ctx);
        _resolve = Resolve.instance(ctx);
        _attr = Attr.instance(ctx);
        _log = Log.instance(ctx);
    }

    protected Symbol findMemberType (Env<DetypeContext> env, Name name, TypeSymbol c)
    {
        Debug.log("Checking for " + name + " as member of " + c);
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

    protected <T> T invoke (Env<DetypeContext> env, Backdoor<T> door,
                            Object receiver, Object... args)
    {
        JavaFileObject ofile = _log.useSource(env.toplevel.getSourceFile());
        try {
            return door.invoke(receiver, args);
        } finally {
            _log.useSource(ofile);
        }
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
    protected Names _names;
    protected Resolve _resolve;
    protected Attr _attr;
    protected Log _log;

    protected static final Context.Key<Resolver> RESOLVER_KEY = new Context.Key<Resolver>();
}
