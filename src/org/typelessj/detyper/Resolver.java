//
// $Id$

package org.typelessj.detyper;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.BoundKind;
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
import com.sun.tools.javac.util.Warner;

import org.typelessj.runtime.Debug;

/**
 * Handles some simple name resolution tasks.
 */
public class Resolver
{
    /** Used to return data from {@link #resolveMethod}. */
    public static class MethInfo {
        public Type site;
        public Symbol msym;
        public List<Type> atypes;
        public List<Type> tatypes;
    }

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
     * Resolves the supplied method invocation into a symbol using information in the supplied
     * context. Performs static resolution to choose between overloaded candidates.
     */
    public MethInfo resolveMethod (Env<DetypeContext> env, JCMethodInvocation mexpr)
    {
        Name mname = TreeInfo.name(mexpr.meth);
        MethInfo mi = new MethInfo();
        mi.msym = _syms.errSymbol; // assume failure! we're so optimistic

        // resolve our argument and type argument types
        mi.atypes = resolveTypes(env, mexpr.args, Kinds.VAL);
        mi.tatypes = resolveTypes(env, mexpr.typeargs, Kinds.TYP);

        // convert any wildcard types to their erasure; I'm not sure why or whether this is
        // strictly necessary, but Resolve's method throw assertion failures if I don't...
        mi.atypes = eraseWildcards(mi.atypes);

        // if we failed to resolve any of our argument types, we need to stop now and return an
        // error symbol
        if (mi.tatypes.contains(null) || mi.atypes.contains(null)) {
            return mi;
        }

        switch (mexpr.meth.getTag()) {
        case JCTree.IDENT: {
            Symbol sym;
            mi.site = env.enclClass.sym.type;
            // pass the buck to javac's Resolve to do the heavy lifting
            if (mname == _names._this || mname == _names._super) {
                if (mname == _names._super) {
                    if (mi.site == _syms.objectType) {
                        mi.site = _types.createErrorType(_syms.objectType);
                    } else {
                        mi.site = _types.supertype(mi.site);
                    }
                }
                // Debug.log("Resolving " + mname + "<" + mi.tatypes + ">(" + mi.atypes + ")");
                mi.msym = invoke(env, Backdoor.resolveConstructor, _resolve, mexpr.pos(),
                                 Detype.toAttrEnv(env), mi.site, mi.atypes, mi.tatypes);
            } else {
                // Debug.log("Resolving " + mname + "<" + mi.tatypes + ">(" + mi.atypes + ")");
                mi.msym = invoke(env, Backdoor.resolveMethod, _resolve, mexpr.pos(),
                                 Detype.toAttrEnv(env), mname, mi.atypes, mi.tatypes);
            }
            if (mi.msym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve method", "expr", mexpr);
            }
            // Debug.log("Asked javac to resolve method " + mexpr + " got " + mi.msym);
            return mi;
        }

        case JCTree.SELECT: {
            // we erase the type parameters from the site because we want javac to ignore the type
            // arguments when resolving our method (to be maximally lenient)
            // Debug.log("Resolving method receiver", "expr", mexpr);
            mi.site = resolveType(env, ((JCFieldAccess)mexpr.meth).selected, Kinds.VAL | Kinds.TYP);
            if (mi.site == null) {
                Debug.warn("Can't resolve receiver type", "expr", mexpr);
                return mi;
            }
            // Debug.log("Resolved method receiver", "expr", mexpr, "site", mi.site);

            // the receiver may also be a wildcard (or a type variable), in which case we need to
            // erase as above
            if (mi.site.tag == TypeTags.WILDCARD) {
                mi.site = _types.erasure(mi.site);
            }

            // pass the buck to javac's Resolve to do the heavy lifting
            // Debug.log("Resolving {"+mi.site+"}." + mname + "<"+mi.tatypes+">("+mi.atypes+")");
            mi.msym = invoke(env, Backdoor.resolveQualifiedMethod, _resolve, mexpr.pos(),
                             Detype.toAttrEnv(env), mi.site, mname, mi.atypes, mi.tatypes);
            if (mi.msym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve method", "expr", mexpr, "site", mi.site);
            }
            // Debug.log("Asked javac to resolve method " + mexpr + " got " + mi.msym);
            return mi;
        }

        default:
            throw new IllegalArgumentException("Method not ident or select? " + mexpr);
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
        // if we already have a resolved type, just use that
        if (expr.type != null) {
            // Debug.log("Using expression type", "expr", expr, "pkind", pkind, "type", expr.type);
            return expr.type;
        }

        // Debug.log("Resolving type", "expr", expr, "pkind", pkind);
        switch (expr.getTag()) {
        case JCTree.IDENT: {
            Name name = TreeInfo.name(expr);
            Symbol sym;
            if (name == _names._this) {
                sym = env.enclClass.sym;
            } else {
                // Debug.log("Resoving ident", "name", name, "pkind", pkind);
                sym = invoke(env, Backdoor.resolveIdent, _resolve,
                             expr.pos(), Detype.toAttrEnv(env), name, pkind);
            }
            if (sym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve type of ident", "expr", expr, "sym", sym);
            }
            return sym.type;
        }

        case JCTree.SELECT: {
            JCFieldAccess facc = (JCFieldAccess)expr;

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

            // if the site is an array and the field is 'length', it's just an int (for some reason
            // Attr.selectSym() doesn't handle .length)
            if (site.tag == TypeTags.ARRAY && facc.name == _names.length) {
                return _syms.typeOfTag[TypeTags.INT];
            }

            // Debug.log("Resolving type symbol", "site", site, "facc", facc);
            Symbol sym = invoke(env, Backdoor.selectSym, _attr, facc, site, Detype.toAttrEnv(env),
                                Type.noType, pkind);
            if (sym == null) {
                Debug.warn("Unable to resolve symbol for field select", "expr", expr, "site", site);
                return null;
            }
            return sym.type;
        }

        case JCTree.APPLY: {
            final Env<DetypeContext> fenv = env;
            MethInfo mi = resolveMethod(env, (JCMethodInvocation)expr);
            if (mi.msym.kind >= Kinds.ERR) {
                return null;
            }
            // if the method is universally quantified, we need to bind its type variables
            // based on the types of its actual arguments
            Type mtype;
            if (mi.msym.type.tag == TypeTags.FORALL) {
                // Resolve.instantiate handles member type conversion for us
                boolean useVarargs = false; // TODO
                mtype = invoke(fenv, Backdoor.instantiate, _resolve, Detype.toAttrEnv(fenv),
                               mi.site, mi.msym, mi.atypes, mi.tatypes, true, useVarargs,
                               new Warner());
            } else {
                // otherwise we just need to convert it to a member type
                mtype = _types.memberType(mi.site, mi.msym);
            }
            return mtype.asMethodType().restype;
        }

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
        case JCTree.NOT: // !
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
        case JCTree.COMPL: // ~
        case JCTree.PREINC: // ++ _
        case JCTree.PREDEC: // -- _
        case JCTree.POSTINC: // _ ++
        case JCTree.POSTDEC: // _ --
            return _syms.typeOfTag[TypeTags.INT]; // TODO: is this true?

        case JCTree.PLUS: { // +
            // if lhs or rhs is string, then expr is string
            Type lhs = resolveType(env, ((JCBinary)expr).lhs, pkind);
            Type rhs = resolveType(env, ((JCBinary)expr).rhs, pkind);
            if (lhs == _syms.stringType || rhs == _syms.stringType) {
                return _syms.stringType;
            } else {
                return lhs; // TODO: numeric promotion
            }
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

        case JCTree.TYPEAPPLY: {
            JCTypeApply tapp = (JCTypeApply)expr;
            Type clazz = resolveType(env, tapp.clazz, Kinds.TYP);
            List<Type> actuals = resolveTypes(env, tapp.arguments, Kinds.TYP);
            Type clazzOuter = clazz.getEnclosingType();
            return new Type.ClassType(clazzOuter, actuals, clazz.tsym);
        }

        case JCTree.WILDCARD: {
            JCWildcard wc = (JCWildcard)expr;
            Type type = (wc.kind.kind == BoundKind.UNBOUND) ? _syms.objectType :
                resolveType(env, wc.inner, Kinds.TYP);
            return new Type.WildcardType(type, wc.kind.kind, _syms.boundClass);
        }

        case JCTree.NEWARRAY:
            return new Type.ArrayType(
                resolveType(env, ((JCNewArray)expr).elemtype, Kinds.TYP), _syms.arrayClass);

        case JCTree.ASSIGN:
            return resolveType(env, ((JCAssign)expr).lhs, Kinds.VAR);

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

    protected List<Type> eraseWildcards (List<Type> types)
    {
        if (types.isEmpty()) return List.<Type>nil();
        return eraseWildcards(types.tail).prepend(
            ((types.head.tag == TypeTags.WILDCARD) ? _types.erasure(types.head) : types.head));
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
