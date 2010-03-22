//
// $Id$

package org.ductilej.detyper;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Infer;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Warner;

import org.ductilej.runtime.Debug;

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
     * Resolves the symbol for the supplied expression. Currently only handles idents and select
     * expressions.
     */
    public Symbol resolveSymbol (Env<DetypeContext> env, JCTree expr, int pkind)
    {
        Symbol sym;
        switch (expr.getTag()) {
        case JCTree.IDENT: {
            Name name = TreeInfo.name(expr);
            if (name == _names._this) {
                sym = env.enclClass.sym;
            } else {
                // Debug.temp("Resoving ident", "name", name, "pkind", pkind);
                sym = invoke(env, Backdoor.resolveIdent, _resolve,
                             expr.pos(), Detype.toAttrEnv(env), name, pkind);
            }
            break;
        }

        case JCTree.SELECT: {
            JCFieldAccess facc = (JCFieldAccess)expr;
            Type site = resolveSelectSite(env, facc, pkind);
            if (site == null) {
                Debug.warn("Unable to resolve receiver of field select: " + expr);
                return _syms.errSymbol;
            }
            sym = invoke(env, Backdoor.selectSym, _attr, facc, site, Detype.toAttrEnv(env),
                         Type.noType, pkind);
            break;
        }

        default:
            Debug.warn("Unknown expr type in resolveSymbol()", "tag", expr.getTag(), "expr", expr,
                       "etype", expr.getClass().getSimpleName());
            return _syms.errSymbol;
        }

        if (sym.kind >= Kinds.ERR) {
            Debug.warn("Symbol resolution failed", "expr", expr, "sym", sym);
        }
        return sym;
    }

    /**
     * Resolves a constructor for the specified class, given the supplied arguments and type
     * arguments, using information in the supplied context. Performs static resolution to choose
     * between overloaded candidates.
     */
    public MethInfo resolveConstructor (Env<DetypeContext> env, JCExpression clazz,
                                        List<JCExpression> args, List<JCExpression> typeargs)
    {
        MethInfo mi = resolveArgs(env, args, typeargs);
        if (mi.tatypes.contains(null) || mi.atypes.contains(null)) {
            return mi;
        }

        mi.site = resolveType(env, clazz, Kinds.TYP);
        if (mi.site == null) {
            Debug.warn("Can't resolve class for ctor", "expr", clazz);
            return mi;
        }

        // Debug.log("Resolving ctor " + mi.site + "<" + mi.tatypes + ">(" + mi.atypes + ")");
        mi.msym = invoke(env, Backdoor.resolveConstructor, _resolve, clazz.pos(),
                         Detype.toAttrEnv(env), mi.site, mi.atypes, mi.tatypes);
        if (mi.msym.kind >= Kinds.ERR) {
            Debug.warn("Unable to resolve ctor", "clazz", clazz, "args", args, "targrs", typeargs);
        }
        // Debug.log("Asked javac to resolve ctor " + clazz + " got " + mi.msym);
        return mi;
    }

    /**
     * Resolves the supplied method invocation into a symbol using information in the supplied
     * context. Performs static resolution to choose between overloaded candidates.
     */
    public MethInfo resolveMethod (Env<DetypeContext> env, JCMethodInvocation mexpr)
    {
        MethInfo mi = resolveArgs(env, mexpr.args, mexpr.typeargs);
        if (mi.tatypes.contains(null) || mi.atypes.contains(null)) {
            return mi;
        }
        Name mname = TreeInfo.name(mexpr.meth);

        switch (mexpr.meth.getTag()) {
        case JCTree.IDENT: {
            Symbol sym;
            mi.site = env.enclClass.sym.type;

            // pass the buck to javac's Resolve to do the heavy lifting
            Env<AttrContext> aenv = Detype.toAttrEnv(env);
            if (mname == _names._this || mname == _names._super) {
                if (mname == _names._super) {
                    if (_types.isSameType(mi.site, _syms.objectType)) {
                        mi.site = _types.createErrorType(_syms.objectType);
                    } else {
                        mi.site = _types.supertype(mi.site);
                    }
                    // we need to twiddle the selectSuper bit in the env we pass to Resolve
                    Backdoor.selectSuper.set(aenv.info, true);
                }
                // Debug.log("Resolving " + mname + "<" + mi.tatypes + ">(" + mi.atypes + ")");
                mi.msym = invoke(env, Backdoor.resolveConstructor, _resolve, mexpr.pos(),
                                 aenv, mi.site, mi.atypes, mi.tatypes);
            } else {
                // Debug.log("Resolving " + mname + "<" + mi.tatypes + ">(" + mi.atypes + ")");
                mi.msym = invoke(env, Backdoor.resolveMethod, _resolve, mexpr.pos(),
                                 aenv, mname, mi.atypes, mi.tatypes);
            }
            if (mi.msym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve method", "expr", mexpr, "site", mi.site,
                           "encl", env.enclClass.sym);
            }
            // Debug.log("Asked javac to resolve method " + mexpr + " got " + mi.msym);
            return mi;
        }

        case JCTree.SELECT: {
            // we erase the type parameters from the site because we want javac to ignore the type
            // arguments when resolving our method (to be maximally lenient)
            // Debug.temp("Resolving method receiver", "expr", mexpr);
            JCExpression selexp = ((JCFieldAccess)mexpr.meth).selected;
            mi.site = resolveType(env, selexp, Kinds.VAL | Kinds.TYP);
            if (mi.site == null) {
                Debug.warn("Can't resolve receiver type", "expr", mexpr);
                return mi;
            }
            // Debug.temp("Resolved method receiver", "expr", mexpr, "site", mi.site);

            // the receiver may also be a wildcard (or a type variable), in which case we need to
            // convert it to its upper bound
            if (mi.site.tag == TypeTags.WILDCARD || mi.site.tag == TypeTags.TYPEVAR) {
                mi.site = mi.site.getUpperBound();
            }

            // if our site is 'super' we need to twiddle a bit in the env we pass to Resolve
            Env<AttrContext> aenv = Detype.toAttrEnv(env);
            if (selexp instanceof JCIdent && ((JCIdent)selexp).name == _names._super) {
                Backdoor.selectSuper.set(aenv.info, true);
            }

            // Debug.temp("Resolving {"+mi.site+"}." + mname + "<"+mi.tatypes+">("+mi.atypes+")");
            mi.msym = invoke(env, Backdoor.resolveQualifiedMethod, _resolve, mexpr.pos(),
                             aenv, mi.site, mname, mi.atypes, mi.tatypes);
            if (mi.msym.kind >= Kinds.ERR) {
                Debug.warn("Unable to resolve method", "expr", mexpr, "site", mi.site);
            }
            // Debug.temp("Asked javac to resolve method " + mexpr + " got " + mi.msym);
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
            // Debug.temp("Using expression type", "expr", expr, "pkind", pkind, "type", expr.type);
            return expr.type;
        }

        // Debug.temp("Resolving type", "expr", expr, "pkind", pkind);
        switch (expr.getTag()) {
        case JCTree.IDENT: {
            Symbol sym = resolveSymbol(env, expr, pkind);

            Env<DetypeContext> env1 = env;
            if (sym.kind < Kinds.ERR && sym.owner != null && sym.owner != env1.enclClass.sym) {
// TODO: we'll need this eventually
//                 // If the found symbol is inaccessible, then it is accessed through an enclosing
//                 // instance.  Locate this enclosing instance:
//                 while (env1.outer != null && !rs.isAccessible(env, env1.enclClass.sym.type, sym))
//                     env1 = env1.outer;
            }

            // Attr.checkId does some small massaging of types that we need to emulate here
            return typeFromSym(expr, env1.enclClass.sym.type, sym);
        }

        case JCTree.SELECT: {
            JCFieldAccess facc = (JCFieldAccess)expr;

            // we'd just use resolveSymbol(), but annoyingly we need the array 'length' handling
            // interjected between site type resolution and symbol resolution; sigh...
            Type site = resolveSelectSite(env, facc, pkind);
            if (site == null) {
                Debug.warn("Unable to resolve receiver of field select: " + expr);
                return null;
            }

            // if the site is an array and the field is 'length', it's just an int (for some reason
            // Attr.selectSym() doesn't handle .length)
            if (site.tag == TypeTags.ARRAY && facc.name == _names.length) {
                return _syms.typeOfTag[TypeTags.INT];
            }

            Symbol sym = invoke(env, Backdoor.selectSym, _attr, facc, site, Detype.toAttrEnv(env),
                                Type.noType, pkind);
            return _types.memberType(site, sym);
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
                if (mtype == null) {
                    Debug.warn("Failed to instantiate forall type", "sym", mi.msym);
                    return null;
                }
            } else {
                // otherwise we just need to convert it to a member type
                mtype = _types.memberType(mi.site, mi.msym);
            }

            // if have a universally quantified return type, we need to instantiate the remaining
            // type variables to their upper bounds; this is normally handled in a twisty maze of
            // calls originating from Attr.checkReturn
            Type rtype = mtype.asMethodType().restype;
            if (rtype.tag == TypeTags.FORALL) {
                // TODO: if we have an expected type (i.e. we're in an initializer expression),
                // at some point we're going to need that here instead of Object; oh boy!
                rtype = _infer.instantiateExpr((Type.ForAll)rtype, _syms.objectType, new Warner());
            }
            
            return rtype;
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
            return _syms.typeOfTag[TypeTags.INT]; // TODO: should be numeric promotion

        case JCTree.POS: // +
        case JCTree.NEG: // -
        case JCTree.COMPL: // ~
            return _syms.typeOfTag[TypeTags.INT]; // TODO: should be numeric promotion

        case JCTree.PREINC: // ++ _
        case JCTree.PREDEC: // -- _
        case JCTree.POSTINC: // _ ++
        case JCTree.POSTDEC: // _ --
            return resolveType(env, ((JCUnary)expr).arg, Kinds.VAR);

        case JCTree.PLUS: { // +
            // if lhs or rhs is string, then expr is string
            Type lhs = resolveType(env, ((JCBinary)expr).lhs, pkind);
            Type rhs = resolveType(env, ((JCBinary)expr).rhs, pkind);
            if (_types.isSameType(lhs, _syms.stringType) ||
                _types.isSameType(rhs, _syms.stringType)) {
                return _syms.stringType;
            } else {
                return lhs; // TODO: numeric promotion
            }
        }

        case JCTree.PLUS_ASG: { // +=
            // if lhs is string, then expr is string
            Type lhs = resolveType(env, ((JCAssignOp)expr).lhs, pkind);
            if (_types.isSameType(lhs, _syms.stringType)) {
                return _syms.stringType;
            } else {
                return lhs; // TODO: numeric promotion
            }
        }

        case JCTree.BITOR_ASG: // |=
        case JCTree.BITXOR_ASG: // ^=
        case JCTree.BITAND_ASG: // &=
            return _syms.typeOfTag[TypeTags.INT]; // TODO: is this true?

        case JCTree.SL_ASG: // <<=
        case JCTree.SR_ASG: // >>=
        case JCTree.USR_ASG: // >>>=
        case JCTree.MINUS_ASG: // -=
        case JCTree.MUL_ASG: // *=
        case JCTree.DIV_ASG: // /=
        case JCTree.MOD_ASG: // %=
            return _syms.typeOfTag[TypeTags.INT]; // TODO: is this true?

        case JCTree.LITERAL: {
            int tag = ((JCLiteral)expr).typetag;
            // TODO: are there other literals that don't have direct type tag mapping?
            return (tag == TypeTags.CLASS) ? _syms.stringType : _syms.typeOfTag[tag];
        }

        case JCTree.TYPEIDENT:
            return _syms.typeOfTag[((JCPrimitiveTypeTree)expr).typetag];

        case JCTree.TYPEARRAY: {
            Type etype = resolveType(env, ((JCArrayTypeTree)expr).elemtype, Kinds.TYP);
            return (etype == null) ? null : new Type.ArrayType(etype, _syms.arrayClass);
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
                return (sym != null) && ((sym.kind == Kinds.TYP) || (sym.kind == Kinds.PCK));
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
            return (sym != null) && ((sym.kind == Kinds.TYP) || (sym.kind == Kinds.PCK));
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
        _infer = Infer.instance(ctx);
        _log = Log.instance(ctx);
    }

    protected <R, V> V invoke (Env<DetypeContext> env, Backdoor.MethodRef<R, V> method,
                               R receiver, Object... args)
    {
        JavaFileObject ofile = _log.useSource(env.toplevel.getSourceFile());
        try {
            return method.invoke(receiver, args);
        } finally {
            _log.useSource(ofile);
        }
    }

    protected List<Type> upperBounds (List<Type> types)
    {
        if (types.isEmpty()) {
            return List.nil();
        }
        Type ubound = (types.head == null) ? null : _types.upperBound(types.head);
        return upperBounds(types.tail).prepend(ubound);
    }

    protected Type resolveSelectSite (Env<DetypeContext> env, JCFieldAccess facc, int pkind)
    {
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
        return resolveType(env, facc.selected, skind);
    }

    /**
     * Helper for {@link #resolveConstructor} and {@link #resolveMethod}.
     */
    protected MethInfo resolveArgs (Env<DetypeContext> env, List<JCExpression> args,
                                    List<JCExpression> typeargs)
    {
        MethInfo mi = new MethInfo();
        mi.msym = _syms.errSymbol; // assume failure! we're so optimistic

        // resolve our argument and type argument types
        mi.atypes = resolveTypes(env, args, Kinds.VAL);
        mi.tatypes = resolveTypes(env, typeargs, Kinds.TYP);

        // convert any wildcard types to their upper bounds; I'm not sure why this is strictly
        // necessary, but Resolve's methods throw assertion failures if I don't...
        mi.atypes = upperBounds(mi.atypes);

        return mi;
    }

    /**
     * Helper for {@link #resolveType} that mimics some sneaky type adjustment in
     * <code>Attr.checkId()</code>.
     */
    protected Type typeFromSym (JCTree tree, Type site, Symbol sym)
    {
        // For types, the computed type equals the symbol's type, except for two situations:
        Type rtype;
        switch (sym.kind) {
        case Kinds.TYP:
            rtype = sym.type;
            if (rtype.tag == TypeTags.CLASS) {
                Type ownOuter = rtype.getEnclosingType();

                // (a) If the symbol's type is parameterized, erase it because no type
                // parameters were given.
                // We recover generic outer type later in visitTypeApply.
                if (rtype.tsym.type.getTypeArguments().nonEmpty()) {
                    rtype = _types.erasure(rtype);
                }

//                     // (b) If the symbol's type is an inner class, then we have to interpret its
//                     // outer type as a superclass of the site type. Example:
//                     //
//                     // class Tree<A> { class Visitor { ... } }
//                     // class PointTree extends Tree<Point> { ... }
//                     // ...PointTree.Visitor...
//                     //
//                     // Then the type of the last expression above is Tree<Point>.Visitor.
//                     else if (ownOuter.tag == CLASS && site != ownOuter) {
//                         Type normOuter = site;
//                         if (normOuter.tag == CLASS)
//                             normOuter = _types.asEnclosingSuper(site, ownOuter.tsym);
//                         if (normOuter == null) // perhaps from an import
//                             normOuter = _types.erasure(ownOuter);
//                         if (normOuter != ownOuter)
//                             rtype = new ClassType(normOuter, List.<Type>nil(), rtype.tsym);
//                     }
            }
            break;

        case Kinds.VAR: {
            VarSymbol v = (VarSymbol)sym;
            // The computed type of a variable is the type of the variable symbol, taken as a
            // member of the site type.
            rtype = (sym.owner.kind == Kinds.TYP &&
                     sym.name != _names._this && sym.name != _names._super) ?
                _types.memberType(site, sym) : sym.type;

// TODO
//             if (env.info.tvars.nonEmpty()) {
//                 Type owntype1 = new ForAll(env.info.tvars, owntype);
//                 for (List<Type> l = env.info.tvars; l.nonEmpty(); l = l.tail)
//                     if (!owntype.contains(l.head)) {
//                         log.error(tree.pos(), "undetermined.type", owntype1);
//                         owntype1 = types.createErrorType(owntype1);
//                     }
//                 owntype = owntype1;
//             }

// TODO
//             // If the variable is a constant, record constant value in computed type.
//             if (v.getConstValue() != null && isStaticReference(tree))
//                 owntype = owntype.constType(v.getConstValue());

//             if (pkind == VAL) {
//                 owntype = capture(owntype); // capture "names as expressions"
//             }
            break;
        }            

        case Kinds.MTH:
// for now fall through
//             case MTH: {
//                 JCMethodInvocation app = (JCMethodInvocation)env.tree;
//                 owntype = checkMethod(site, sym, env, app.args,
//                                       pt.getParameterTypes(), pt.getTypeArguments(),
//                                       env.info.varArgs);
//                 break;
//             }

        case Kinds.PCK:
        case Kinds.ERR:
            rtype = sym.type;
            break;

        default:
            throw new AssertionError("Unexpected kind: " + sym.kind + " in tree " + tree);
        }

        return rtype;
    }

    protected ClassReader _reader;
    protected Types _types;
    protected Symtab _syms;
    protected Names _names;
    protected Resolve _resolve;
    protected Attr _attr;
    protected Infer _infer;
    protected Log _log;

    protected static final Context.Key<Resolver> RESOLVER_KEY = new Context.Key<Resolver>();
}