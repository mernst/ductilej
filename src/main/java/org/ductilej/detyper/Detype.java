//
// $Id$

package org.ductilej.detyper;

import javax.tools.JavaFileObject;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.file.BaseFileObject;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name; // Name.Table -> Names in OpenJDK
import com.sun.tools.javac.util.Names;

import org.ductilej.runtime.Binop;
import org.ductilej.runtime.Debug;
import org.ductilej.runtime.RT;
import org.ductilej.runtime.Transformed;
import org.ductilej.runtime.Unop;
import org.ductilej.util.ASTUtil;
import org.ductilej.util.PathedTreeTranslator;

/**
 * Performs the actual detyping.
 */
public class Detype extends PathedTreeTranslator
{
    /** Whether or not to remove methods from interfaces (TEMP). */
    public static boolean KEEPIFCS = Boolean.getBoolean("org.ductilej.keepifcs");

    /** Whether or not to use full coercions in place of implicit widenings. */
    public static boolean COERCEALL = Boolean.getBoolean("org.ductilej.coerceall");

    /**
     * Returns the detyping tree translator.
     */
    public static Detype instance (Context context)
    {
        Detype instance = context.get(DETYPE_KEY);
        if (instance == null) {
            instance = new Detype(context);
        }
        return instance;
    }

    /**
     * Converts the supplied detype context into an attr context. Note: the resulting context will
     * not contain everything an AttrContext might normally contain. This is only usable in
     * situations where you carefully verify that javac's internals aren't relying on things this
     * conversion does not provide.
     */
    public static Env<AttrContext> toAttrEnv (Env<DetypeContext> env)
    {
        if (env == null) {
            return null;
        }

        // copy over (translating along the way) our Env bits
        Env<AttrContext> aenv = new Env<AttrContext>(env.tree, new AttrContext());
        aenv.next = toAttrEnv(env.next);
        aenv.outer = toAttrEnv(env.outer);
        aenv.toplevel = env.toplevel;
        aenv.enclClass = env.enclClass;
        aenv.enclMethod = env.enclMethod;
        aenv.baseClause = env.baseClause; // not used by detype

        // copy over detype context parts that are useful to attr context
        Backdoor.scope.set(aenv.info, env.info.scope);
        Backdoor.lint.set(aenv.info, env.info.lint);

        return aenv;
    }

    /**
     * Detypes the supplied compilation unit.
     */
    public void detype (JCCompilationUnit tree)
    {
        Env<DetypeContext> oenv = _env;
        try {
            _env = new Env<DetypeContext>(tree, new DetypeContext());
            _env.toplevel = tree;
            _env.enclClass = _predefClassDef;
            _env.info.scope = tree.namedImportScope;
            _env.info.lint = _lint;
            tree.accept(this);
        } catch (RuntimeException rte) {
            Debug.warn("Fatal error", "file", tree.sourcefile);
            throw rte;
        } finally {
            _env = oenv;
        }
    }

    @Override public void visitTopLevel (JCCompilationUnit tree)
    {
        _tmaker = _rootmaker.forToplevel(tree);
        super.visitTopLevel(tree);
    }

    @Override public void visitImport (JCImport tree) {
        super.visitImport(tree);

        // if this is a static method import, we want to remove it; the method being imported may
        // be renamed and in any event will not appear in the detyped source of this class (all
        // calls will be rewritten to invokeStatic)
        if (tree.isStatic() && TreeInfo.name(tree.qualid) != _names.asterisk &&
            !isVarImport(tree)) {
            result = _tmaker.at(tree.pos).Skip();
        }
    }

    @Override public void visitClassDef (JCClassDecl tree)
    {
        Debug.log("Visiting class '" + tree.name + "'", "sym", tree.sym);

        // don't detype annotation classes; that causes myriad problems
        if ((tree.mods.flags & Flags.ANNOTATION) != 0) {
            result = tree;
            return;
        }

        // local classes have not yet been entered, so we must manually do so
        JCClassDecl ctree = tree;
        if (ctree.sym == null) {
            // clone the tree to avoid modifying the AST in ways that will confuse javac later
            ctree = (JCClassDecl)ctree.clone();

            JavaFileObject ofile = _log.useSource(_env.toplevel.getSourceFile());
            try {
                Backdoor.classEnter.invoke(_enter, ctree, toAttrEnv(_env));
            } finally {
                _log.useSource(ofile);
            }

            // the above call set up MemberEnter as a completer, we must now trigger it to cause
            // entry to take place on all of the inner class's members
            ctree.sym.complete();
            Debug.log("Entered inner class '" + ctree.name + "'", "sym", ctree.sym);
            // ASTUtil.dumpSyms(ctree);
        }

        // the entering process should have created an environment for the entered class, we need
        // to extract its scope and use that in our environment so that if/when we ask Resolve to
        // do things, it ends up using the correct scope
        Env<AttrContext> eenv = _enter.getEnv(ctree.sym);
        // however, anonymous inner classes seem not to have an environment created?
        Scope escope;
        if (eenv == null) {
            Debug.warn("No enter scope for " + ctree.sym);
            escope = new Scope(ctree.sym);
        } else {
            escope = Backdoor.scope.get(eenv.info);
        }

        // note the environment of the class we're processing
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(ctree, oenv.info.dup(escope));
        _env.enclClass = ctree;
        _env.outer = oenv;

        if (tree.name != _names.empty) {
            // add our @Transformed annotation to the AST
            JCAnnotation a = _tmaker.at(tree.pos).Annotation(
                mkFA(Transformed.class.getName(), tree.pos), List.<JCExpression>nil());
            tree.mods.annotations = tree.mods.annotations.prepend(a);

            // since the annotations AST has already been resolved into type symbols, we have
            // to manually add a type symbol for annotation to the ClassSymbol
            if (tree.sym != null) {
                tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                    Backdoor.enterAnnotation.invoke(
                        _annotate, a, _syms.annotationType, _enter.getEnv(tree.sym)));
            }
        }

        // if this is an interface declaration, remove all method declarations; we don't need them
        // since all application methods are called reflectively, and if we leave them here, they
        // cause problems if classes don't implement the entire interface (or inherit
        // implementations from library classes)
        if (!KEEPIFCS /*temp*/ && (ctree.sym.flags() & Flags.INTERFACE) != 0) {
            tree.defs = removeMethodDefs(tree.defs);
        }

        super.visitClassDef(ctree);
        result = tree; // finally restore our unmodified tree
        _env = oenv;
    }

    @Override public void visitMethodDef (JCMethodDecl tree)
    {
        Debug.log("Visiting method def", "name", tree.name);

        // if we're visiting a method in a local or anonymous inner class, we need to fake up
        // symbols for our methods so that any local or anonymous inner classes therein have value
        // owner symbols; normally this happens when classes are completed, but we can't complete
        // our fake-entered classes without causing the compiler to fall over later when it goes to
        // do everything properly
        if (tree.sym == null) {
            Scope enclScope = (_env.tree.getTag() == JCTree.CLASSDEF) ?
                ((JCClassDecl) _env.tree).sym.members_field : _env.info.scope;
//             Type msig = Backdoor.signature.invoke(_memberEnter, tree.typarams, tree.params,
//                                                   tree.restype, tree.thrown, toAttrEnv(_env));
            Type msig = null;
            // Debug.temp("Computed sig " + msig + " for " + tree);
            tree.sym = new MethodSymbol(0, tree.name, msig, enclScope.owner);
            // tree.sym.flags_field = chk.checkFlags(tree.pos(), tree.mods.flags, m, tree);
        }

        // in very limited circumstances (currently only for JUnit TestCase constructors) we leave
        // things completely intact (detyping neither the arguments nor the method body)
        if (isUndetypableMethod(tree)) {
            result = tree;
            return;
        }

        // create a local environment for this method definition
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(oenv.info.scope.dupUnshared()));
        _env.enclMethod = tree;
        _env.info.scope.owner = tree.sym;

        // enter all type parameters into the local method scope
        for (List<JCTypeParameter> l = tree.typarams; l.nonEmpty(); l = l.tail) {
            _env.info.scope.enterIfAbsent(l.head.type.tsym);
        }

        // if we're not in a library overrider, prepare our type-carrying arguments (before we call
        // super which will erase our argument's types)
        boolean isLib = inLibraryOverrider();
        // note: toTypeArgs has the side-effect that it turns off the varargs flag on a varargs
        // final argument because it "moves" the varargs flag to the final type argument instead
        List<JCVariableDecl> sigargs = isLib ? List.<JCVariableDecl>nil() : toTypeArgs(tree.params);

        // now we can call super and translate our children
        super.visitMethodDef(tree);

        // remove @Override annotations because those may cause problems if they exist on an
        // application interface method which has been removed from the interface
        tree.mods.annotations = removeOverrideAnnotation(tree.mods.annotations);

        // transform the return type if we're not in a library overrider and it is not void
        if (tree.restype != null && !ASTUtil.isVoid(tree.restype) && !isLib) {
            tree.restype = _tmaker.Ident(_names.fromString("Object"));
        }

        // if we have type-carrying arguments to append, do so now
        if (!sigargs.isEmpty()) {
            // if we're signature mangling a non-constructor, note that in its name
            if (tree.name != _names.init) {
                tree.name = tree.name.append(_names.fromString(RT.MM_SUFFIX));
            }
            tree.params = tree.params.appendList(sigargs);

        // if we are in a library overrider, we need to rename the formal arguments and insert
        // value carrying arguments for use in the method body:
        // void someLibMethod (String arg1, int arg2) { ... } becomes
        // void someLibMethod (String arg1$T, int arg2$T) {
        //     Object arg1 = arg1$T, arg2 = arg2$T; ... }
        } else if (!tree.params.isEmpty() && tree.body != null) {
            // we'll never have to insert shadow arguments into a constructor because a constructor
            // is never a library signature overrider; this it will always be safe to jam
            // declarations at the top of our method body (it would not be safe to do so in a
            // constructor because they'd end up above the super() call)
            for (List<JCVariableDecl> p = tree.params; !p.isEmpty(); p = p.tail) {
                Name valname = p.head.name;
                p.head.name = _names.fromString(valname + "$T");
                tree.body.stats = tree.body.stats.prepend(
                    // preserve the flags (e.g. final), but strip off PARAMETER as our synthesized
                    // shadow field is not, in fact, a method parameter
                    _tmaker.VarDef(_tmaker.Modifiers(p.head.mods.flags & ~Flags.PARAMETER),
                                   valname, typeToTree(_syms.objectType, p.head.pos),
                                   _tmaker.Ident(p.head.name)));
            }
        }

        // restore our previous environment
        _env = oenv;
    }

    @Override public void visitVarDef (JCVariableDecl tree)
    {
        boolean isEnumDecl = (tree.sym != null) && Flags.isEnum(tree.sym);

        // var symbols for member-level variables are already entered, we just want to handle
        // formal parameters and local variable declarations
        if (_env.tree.getTag() != JCTree.CLASSDEF && !isEnumDecl) {
            // create a placeholder VarSymbol for this variable so that we can use it later
            // during some simple name resolution
            Type vtype = _resolver.resolveType(_env, tree.vartype, Kinds.TYP);
            // Debug.temp("Creating var symbol", "name", tree.name, "type", vtype);
            _env.info.scope.enter(new VarSymbol(0, tree.name, vtype, _env.info.scope.owner));
        }

        // we need a new environment here to keep our Env tree isomorphic to javac's
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup());
        if (tree.sym != null && tree.sym.owner != null && tree.sym.owner.kind == Kinds.TYP) {
            _env.info.scope = new Scope.DelegatedScope(_env.info.scope);
            _env.info.scope.owner = tree.sym;
        }
// TODO: do we need static level?
//         if ((tree.mods.flags & STATIC) != 0 ||
//             (_env.enclClass.sym.flags() & INTERFACE) != 0)
//             _Env.info.staticLevel++;

        // if we're declaring an array, put its type in the arrayElemType as we may encounter a
        // bare array initializer expression (i.e. int[] foo = { 1, 2, 3}) and we'll need to use
        // this declared type when detyping the initializer
        if (tree.vartype instanceof JCArrayTypeTree) {
            _env.info.arrayElemType = tree.vartype;
        }

        // if the declaration has primitive type and an initializer, we need to resolve the type of
        // the initializer expression so that we can determine whether we need coercions
        boolean isPrimitive = tree.vartype.getTag() == JCTree.TYPEIDENT;
        Type itype = (!isPrimitive || tree.init == null) ? null :
            _resolver.resolveType(_env, tree.init, Kinds.VAL);

        // determine whether we're defining a constant (a final variable that references a constant
        // expression, e.g. final int foo = 3)
        boolean isConstDecl = isConstDecl(tree);

        // we don't want to detype the initializer of a const field, it would break the const-ness
        // of switch case expressions, and it would prevent the constant from being inlined (the
        // latter in theory shouldn't change semantics but don't ask me to provide a proof)
        if (!isConstDecl) {
            super.visitVarDef(tree);
        } else {
            result = tree;
        }
        _env = oenv;

        String path = path();
        if (isConstDecl || isEnumDecl ||
            // don't detype the param(s) of a catch block
            path.endsWith(".Catch") ||
            // nor the arguments of a library
            (path.contains(".MethodDef.params") && inLibraryOverrider())) {
            return;
        }

        // if we're about to transform a primitive field with no initializer, we need to synthesize
        // an initializer that mimics the default initialization provided for primitive fields
        if (path.endsWith(".ClassDef") && tree.init == null && isPrimitive &&
            // if the field is final, they must assign it a non-default value in the constructor,
            // so we need not (and indeed cannot) supply a synthesized initializer
            !ASTUtil.isFinal(tree.mods)) {
            // if this is an instance field, we have to do some reflection finagling to obtain our
            // default value because the field *may* have already been initialized by an overridden
            // method called from the superclass constructor; static fields pose no such risk
            if ((tree.mods.flags & Flags.STATIC) == 0) {
                tree.init = callRT("initPrimitive", tree.pos,
                                   _tmaker.Ident(_names._this),
                                   _tmaker.Literal(tree.name.toString()),
                                   classLiteral(tree.vartype, tree.pos));
            } else {
                // all primitive literals use (integer) 0 as their value (even boolean)
                tree.init = _tmaker.Literal(((JCPrimitiveTypeTree)tree.vartype).typetag, 0);
            }

        // if the vardef is a primitive and has an initializer, we may need to emulate an implicit
        // narrowing or widening conversion if the rvalue differs from the type of the lvalue
        } else if (isPrimitive && tree.init != null) {
            int vtag = ((JCPrimitiveTypeTree)tree.vartype).typetag;
            // TODO: only implicitly narrow when RHS is a constant expr?
            if (itype != null && vtag != itype.tag &&
                !_types.isSameType(_types.boxedClass(_syms.typeOfTag[vtag]).type, itype)) {
                tree.init = callRT("coerce", tree.init.pos,
                                   classLiteral(tree.vartype, tree.init.pos), tree.init);
            }

        // lastly, if this is just a plain old vardef with no initializer, we add a default
        // initializer to relax the requirement for definite assignment
        } else if (RELAX_DEFASSIGN && tree.init == null &&
                   !path.endsWith(".ClassDef") &&
                   !path.endsWith("MethodDef.params") &&
                   !path.endsWith("Block.ForeachLoop") &&
                   !ASTUtil.isFinal(tree.mods)) {
            tree.init = _tmaker.Literal(
                isPrimitive ? ((JCPrimitiveTypeTree)tree.vartype).typetag : TypeTags.BOT, 0);
        }

        // Debug.log("Xforming vardef", "mods", tree.mods, "name", tree.name, "init", tree.init);
        tree.vartype = _tmaker.Ident(_names.fromString("Object"));
        if (ASTUtil.isVarArgs(tree.mods.flags)) {
            tree.vartype = _tmaker.TypeArray(tree.vartype);
        }
    }

    @Override public void visitReturn (JCReturn tree)
    {
        super.visitReturn(tree);

        // if we're in a method whose signature cannot be transformed, we must cast the result of
        // the return type back to the static method return type
        if (tree.expr != null && inLibraryOverrider()) {
            if (_env.enclMethod.sym.type == null) {
                Debug.warn("Enclosing method missing type?",
                           "class", ((BaseFileObject)_env.toplevel.sourcefile).getShortName(),
                           "meth", _env.enclMethod);
            } else {
                // Debug.temp("Casting back to return type", "meth", _env.enclMethod.sym.type);
                tree.expr = cast(_env.enclMethod.sym.type.asMethodType().restype, tree.expr);
            }
        }
    }

    @Override public void visitUnary (JCUnary tree)
    {
        // we don't call super because mkAssign needs the untranslated expr for ++ and --

        JCExpression expr = unop(tree.pos, tree.getKind(), translate(tree.arg));
        switch (tree.getKind()) {
        case PREFIX_INCREMENT:  // ++i -> (i = unop("++", i))
        case POSTFIX_INCREMENT: // i++ -> unop("--", i = unop("++", i))
            expr = mkAssign(tree.arg, expr, tree.pos);
            if (tree.getKind() == Tree.Kind.POSTFIX_INCREMENT) {
                // we use unop("--", e) here instead of binop("-", e, 1) because unop-- avoids
                // promoting its operand to int
                expr = unop(tree.pos, Tree.Kind.POSTFIX_DECREMENT, expr);
            }
            break;

        case PREFIX_DECREMENT:  // --i -> (i = unop("--", i))
        case POSTFIX_DECREMENT: // i-- -> unop("++", i = unop("--", i))
            expr = mkAssign(tree.arg, expr, tree.pos);
            if (tree.getKind() == Tree.Kind.POSTFIX_DECREMENT) {
                // we use unop("++", e) here instead of binop("+", e, 1) because unop++ avoids
                // promoting its operand to int
                expr = unop(tree.pos, Tree.Kind.POSTFIX_INCREMENT, expr);
            }
            break;
        }

        // Debug.log("Rewrote unop: " + tree + " -> " + expr);
        result = expr;
    }

    @Override public void visitBinary (JCBinary tree)
    {
        super.visitBinary(tree);

        switch (tree.getTag()) {
        case JCTree.AND:
        case JCTree.OR:
            // and and or short-circuit, so we cannot turn them into a two argument function call,
            // we instead have to cast the left- and right-hand-sides back to boolean and leave
            // them inline
            tree.lhs = callRT("asBoolean", tree.lhs.pos, tree.lhs);
            tree.rhs = callRT("asBoolean", tree.rhs.pos, tree.rhs);
            result = tree;
            break;

        default:
            result = binop(tree.pos, tree.getKind(), tree.lhs, tree.rhs);
            // Debug.log("Rewrote binop", "kind", tree.getKind(), "tp", tree.pos);
            break;
        }
    }

    @Override public void visitAssignop (JCAssignOp tree)
    {
        // we don't call super because mkAssign needs the untranslated LHS

        // assign ops implicitly coerce the result back to the type of the left-hand-side, so we
        // need to resolve said lhs type and insert a coercion
        Type ltype = _resolver.resolveType(_env, tree.lhs, Kinds.VAR);

        // we create this temporary JCBinary so that we can call bop.getKind() below which calls
        // into non-public code that would otherwise be annoying to call
        JCExpression bop = _tmaker.Binary(tree.getTag() - JCTree.ASGOffset, tree.lhs, tree.rhs);
        bop = binop(tree.pos, bop.getKind(), translate(tree.lhs), translate(tree.rhs));
        // if the lhs is a primitive, we need to insert a coercion back to its type; this is
        // because 'shortv += intv' is equivalent to 'shortv = (short)(shortv + intv)'.
        if (ltype.isPrimitive()) {
            bop = callRT("coerce", bop.pos, classLiteral(ltype, bop.pos), bop);
        }

        // TODO: this a problem wrt evaluating the LHS more than once, we need emit special code
        // depending on the LHS (i.e. assignOpAt("+", array, val, idx), etc.)
        result = mkAssign(tree.lhs, bop, tree.pos);
        // Debug.log("Rewrote assignop", "kind", tree.getKind(), "into", result);
    }

    @Override public void visitNewClass (JCNewClass tree)
    {
//         Debug.log("Class instantiation", "typeargs", tree.typeargs, "class", what(tree.clazz),
//                   "args", tree.args);

        // we need a new environment here to keep our Env tree isomorphic to javac's
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup());

        // enums are already desugared somewhat by the time we are called; the AST looks like so:
        // public enum Type {
        //     /*public static final*/ ADD /* = new Type() */
        // }
        // we need to specially handle these desugared enum constructor calls
        boolean inEnumFieldInit = path().endsWith(".ClassDef.VarDef") &&
            Flags.isEnum(_env.enclClass.sym);

        // we can only transform the constructor into a reflective call if we're not in an enum
        // field initializer and we're not looking at an anonmyous inner class declaration
        boolean canReflect = (tree.def == null && !inEnumFieldInit);

        // If we are seeing a qualified new, of the form:
        //    <expr>.new C <...> (...) ...
        // we let clazz stand for the name of the allocated class C prefixed with the type of the
        // qualifier expression, so that we can resolve it with standard techniques later. If
        // <expr> has type T, then <expr>.new C <...> (...) yields a clazz T.C.
        JCExpression clazz = tree.clazz;
        Type enctype = null;
        if (tree.encl != null) {
            enctype = _resolver.resolveType(_env, tree.encl, Kinds.VAL);
            JCExpression clazzid = (clazz.getTag() == JCTree.TYPEAPPLY) ?
                ((JCTypeApply) clazz).clazz : clazz;
            clazzid = _tmaker.at(clazz.pos).Select(
                typeToTree(enctype, clazz.pos), ((JCIdent) clazzid).name);
            if (clazz.getTag() == JCTree.TYPEAPPLY) {
                clazz = _tmaker.at(tree.pos).TypeApply(clazzid, ((JCTypeApply) clazz).arguments);
            } else {
                clazz = clazzid;
            }
        }

        Resolver.MethInfo mi = _resolver.resolveConstructor(
            _env, clazz, tree.args, tree.typeargs, tree.def != null);
        Type ctype = mi.isValid() ? mi.site : _resolver.resolveType(_env, clazz, Kinds.TYP);

        // do some special processing for anonymous inner class instantiation expressions
        if (tree.def != null) {
            _env.info.anonParent = ctype.tsym;
            // Attr does some massaging of anonymous JCClassDecls which we need to manually
            // duplicate here because attribution won't happen for a while, but we want to
            // sneakily (and correctly) enter our anonymous inner class
            if (ctype.tsym.isInterface()) {
                tree.def.implementing = List.of(tree.clazz);
            } else {
                tree.def.extending = tree.clazz;
            }
        }

        super.visitNewClass(tree);

        if (canReflect) {
            // if there is a specific enclosing instance provided, pass it to newInstance()
            JCExpression thisex;
            if (tree.encl != null) {
                thisex = tree.encl;
                // we can no longer just use tree.clazz as our class literal because the enclosing
                // reference moves us into its namespace; thus outer.new Inner() needs to result in
                // a reflective instantiation of Outer.Inner.class not just Inner.class; we already
                // generated our fully qualified class name above, so we use it here
                tree.clazz = clazz;

            // if we failed to resolve the class type, just do nothing for now (TODO)
            } else if (ctype == null) {
                thisex = _tmaker.at(tree.pos).Literal(TypeTags.BOT, null);

            // if we're in a static state or have a static inner class, pass null
            } else if (inStatic() || ctype.getEnclosingType() == Type.noType) {
                thisex = _tmaker.at(tree.pos).Literal(TypeTags.BOT, null);

            // otherwise we have to figure out the appropriate implicit enclosing this
            } else {
                // we may be looking at a situation like:
                // class A { class B {} class C { foo() { new B(); }}}
                // in which case "this" is C.this but we really need A.this to instantiate a B, so
                // we have to resolve the "outer" type of the class we're instantiating and compare
                // it to the current enclosing class
                Type otype = _types.erasure(ctype.getEnclosingType());
                if (_types.isSubtype(_types.erasure(_env.enclClass.sym.type), otype)) {
                    thisex = _tmaker.at(tree.pos).Ident(_names._this);
                } else {
                    // we can't just use the owner type as we may be an inner class inside a
                    // subtype of the owner type and we cannot use Parent.this, we must use
                    // Child.this if we're defined in Child; so we step outward through our
                    // enclosing types looking for the type that is a subtype of the owner
                    Type etype = getEnclosingSubtype(
                        otype, _types.erasure(_env.enclClass.sym.type));
                    if (etype == null) {
                        // we failed to find an enclosing subtype, so we're looking at illegal code
                        // (i.e. instantiation of non-static inner type without valid outer type);
                        // pass null as the outer reference and hope for the best
                        thisex = _tmaker.at(tree.pos).Literal(TypeTags.BOT, null);
                    } else {
                        thisex = _tmaker.at(tree.pos).Select(
                            typeToTree(etype, tree.pos), _names._this);
                    }
                }
            }

            // TODO: we can't reflectively create anonymous inner classes so maybe we should not
            // detype any constructor invocation...
            JCMethodInvocation invoke = callRT(
                "newInstance", tree.pos, classLiteral(tree.clazz, tree.clazz.pos),
                mkSigArgs(mi, tree.pos), thisex,
                mkArray(_tmaker.Ident(_names.fromString("Object")), tree.args));
            invoke.varargsElement = tree.varargsElement;
            result = invoke;

        } else { /* !canReflect */
            // if we didn't rewrite the constructor call to newInstance(), we need to insert either
            // runtime casts to the the formal parameter types, or type carrying args
            if (!tree.args.isEmpty() ||
                // if we have a no-args call to a varargs constructor, we can't leave it as a
                // no-args call because the callee may have been detyped; we need to insert an
                // empty array in the varargs position and the type carrying arg for the varargs
                // array; if the callee is a library constructor, the castList() call will noop
                ASTUtil.isVarArgs(mi.msym.flags())) {
                List<Type> ptypes = _resolver.instantiateType(_env, mi).asMethodType().argtypes;
                if (ASTUtil.isLibrary(_env.info.anonParent)) {
                    tree.args = castList(ptypes, tree.args);
                } else {
                    tree.args = addManglingArgs(mi.msym, ptypes, tree.args, mi.atypes, tree.pos);
                }
            }

            // if we have an explicit enclosing instance, we need to insert a cast of that instance
            // back to its static type as it will likely have been detyped
            if (tree.encl != null) {
                tree.encl = _tmaker.TypeCast(typeToTree(enctype, tree.encl.pos), tree.encl);
                // NOTE: we'd like to use 'cast(enctype, tree.encl)' above, but that triggers a bug
                // in javac, so we have to insert a static cast directly; this *could* result in an
                // "unnecessary cast" warning, but c'est la vie
            }
        }

        // finally restore our previous environment
        _env = oenv;
    }

    @Override public void visitNewArray (JCNewArray tree)
    {
        // determine the type of the array elements
        JCExpression etype = tree.elemtype;
        if (etype == null) {
            // if we're seeing something like 'int[] foo = { 1, 2, 3 }' or we're inside a
            // multidimensional initializer like 'int[][] foo = new int[] {{ 1, 2, 3}, { 1 }}'
            // we'll have stuffed our element type into the context
            if (_env.info.arrayElemType instanceof JCArrayTypeTree) {
                etype = ((JCArrayTypeTree)_env.info.arrayElemType).elemtype;
            }
        }

        // note our current array element type in our context
        JCExpression oetype = _env.info.arrayElemType;
        _env.info.arrayElemType = etype;
        // now translate any nested expressions
        super.visitNewArray(tree);
        // restore our previous array element type
        _env.info.arrayElemType = oetype;

        // Debug.log("Array creation", "expr", tree, "dims", tree.dims, "elems", tree.elems);

        // we need to cast the dimension expressions to int
        tree.dims = castIntList(tree.dims);

        if (etype == null) {
            // do nothing, we're probably looking at syntactically incorrect code
        } else if (tree.elems != null) {
            // if our argument is is a single element which is a null literal, we need to add a
            // cast to Object as we're going to translate it into a call of the form:
            //   RT.boxArrayArgs(Type.class, null)
            // which will be an ambiguous use of varargs
            if (tree.elems.size() == 1 && ASTUtil.isNullLiteral(tree.elems.head)) {
                tree.elems.head = toTypedNull(_syms.objectType, tree.elems.head);
            }
            result = callRT("boxArrayArgs", tree.pos,
                            tree.elems.prepend(classLiteral(etype, etype.pos)));
        } else {
            result = callRT("boxArray", tree.pos, classLiteral(etype, etype.pos), tree);
        }
    }

    @Override public void visitSelect (JCFieldAccess tree)
    {
        super.visitSelect(tree);

        // if we know for sure this is a static select, then stop here
        if (!TreeInfo.nonstaticSelect(tree)) {
            return;
        }

        // determine if we're somewhere that we know we shouldn't be fooling around
        String path = path();
        boolean wantXform = true;
        wantXform = wantXform && !path.contains(".TopLevel.pid");
        wantXform = wantXform && !path.contains(".Import");
        wantXform = wantXform && !path.contains(".Annotation");
        wantXform = wantXform && !path.endsWith(".Apply.meth");
        wantXform = wantXform && !path.contains(".VarDef.vartype");
        wantXform = wantXform && !path.contains(".NewClass.clazz");
        wantXform = wantXform && !path.contains(".NewArray.type");
        wantXform = wantXform && !path.contains(".ClassDef.typarams");
        wantXform = wantXform && !path.contains(".ClassDef.extending");
        wantXform = wantXform && !path.contains(".ClassDef.implementing");
        if (!wantXform) {
            return;
        }

        // if the selected expression is a static receiver (a class), or we're looking at a class
        // literal expression (Foo.class) then we don't want to transform, otherwise we do
        if (!_resolver.isStaticSite(_env, tree.selected) && tree.name != _names._class) {
            // transform obj.field into RT.select(obj, "field")
            result = callRT("select", tree.pos, _tmaker.Literal(tree.name.toString()),
                            tree.selected);
        }
    }

    @Override public void visitApply (JCMethodInvocation tree)
    {
//         Debug.temp("Method invocation", "typeargs", tree.typeargs, "method", tree.meth,
//                    "args", tree.args, "varargs", tree.varargsElement);

        assert !tree.meth.toString().startsWith(RT.class.getName()) : "Doubly transforming";

        // if this is a zero args super() call, we'll be doing no detyping and we just stop here;
        // normally going through the motions would be of no consequence, but in the case of
        // synthesized enum code we run into annoying warnings relating to the fact that enums have
        // no legal super constructor
        Name mname = TreeInfo.name(tree.meth);
        if (mname == _names._super && tree.args.isEmpty()) {
            result = tree;
            return;
        }

        // resolve the called method before we transform the leaves of this tree
        Resolver.MethInfo mi = _resolver.resolveMethod(_env, tree);
        Symbol statsym = null;

        // if we're not able to resolve the method, we need to obtain some other information before
        // we call super.visitApply (as it transforms things we need to resolve)
        if (!mi.isValid()) {
            if (tree.meth instanceof JCFieldAccess) {
                statsym = _resolver.inferStaticReceiver(_env, ((JCFieldAccess)tree.meth).selected);
            }
        }

        // Debug.temp("Method invocation", "tree", tree, "sym", mi.msym);

        // we need to track whether we're processing the arguments of a this() or super()
        // constructor because that is a "static" context in that it is illegal to reference "this"
        // during that time; there's no nice way to represent that in path() so instead we track it
        // explicitly in our environment and reference it in inStatic()... elegance--.
        boolean isChainedCons = (mname == _names._super || mname == _names._this);
        boolean oldInChainedCons = _env.info.inChainedCons;
        _env.info.inChainedCons = _env.info.inChainedCons || isChainedCons;
        super.visitApply(tree);
        _env.info.inChainedCons = oldInChainedCons;

        // if this is a chained constructor call or super.foo(), we can't call it reflectively
        if (isChainedCons || isSuperMethodCall(tree)) {
            if (!tree.args.isEmpty()) {
                assert mi.isValid(); // TODO: cope when we can't resolve this() or super()
                // we need to convert any formal type parameters on this method (as defined in
                // the super class) to the actuals provided by our class in the extends clause
                List<Type> ptypes = _resolver.instantiateType(_env, mi).asMethodType().argtypes;
                // if the method is defined in a library class...
                if (ASTUtil.isLibrary(mi.msym.owner) || // either the owner is a library
                    // or we're overriding a detyped class that itself overrides the library
                    ASTUtil.isLibraryOverrider(_types, mi.msym)) {
                    // ...we need to cast the argument types back to the types it expects
                    tree.args = castList(ptypes, tree.args);
                } else {
                    // if the declarer is not a library class, we need to insert type carrying
                    // arguments that match the types of the method we resolved; if the resolved
                    // method is overloaded, this will disambiguate, and even if it's not
                    // overloaded, we need something legal in those argument positions
                    tree.args = addManglingArgs(mi.msym, ptypes, tree.args, mi.atypes, tree.pos);
                    // we also need to append the "is mangled" tag to the method name if we're
                    // looking at a super non-constructor call (e.g. super.foo())
                    if (tree.meth instanceof JCFieldAccess) {
                        ((JCFieldAccess)tree.meth).name = _names.fromString(mname + RT.MM_SUFFIX);
                    }
                }
            }
            return;
        }

        // if the receiver is of type Class<?> and the method being called is newInstance(), we
        // can't wrap the newInstance() call into a reflective call of our own because the default
        // constructor of the class being created may be inaccessible to RT.invoke() and we have no
        // opportunity to make it accessible, so calling it would be an illegal access
        if (mi.isValid() && mname == _names.fromString("newInstance") &&
            _types.isSameType(_types.erasure(mi.site), _types.erasure(_syms.classType))) {
            // insert a cast to Class<?> if we have a non-implicit receiver and it's not a C.class
            // expression (which needs no cast)
            if (tree.meth.getTag() == JCTree.SELECT) {
                JCFieldAccess meth = (JCFieldAccess)tree.meth;
                if (!(meth.selected.getTag() == JCTree.SELECT &&
                      TreeInfo.name(meth.selected) == _names._class)) {
                    meth.selected = cast(_wildcardClass, meth.selected);
                }
            }
            return;
        }

        String invokeName;
        JCExpression recv;
        if (!mi.isValid()) {
            // if we were unable to resolve the target method, try some fallback heuristics to
            // determine whether it has a static or non-static receiver
            if (tree.meth instanceof JCFieldAccess) {
                if (statsym != null) {
                    String fqName = statsym.getQualifiedName().toString();
                    recv = classLiteral(mkFA(fqName, tree.pos), tree.pos);
                    invokeName = "invokeStatic";
                } else {
                    recv = ((JCFieldAccess)tree.meth).selected;
                    invokeName = "invoke";
                }
            } else {
                if (inStatic()) {
                    String fqName = _env.enclClass.sym.getQualifiedName().toString();
                    recv = classLiteral(mkFA(fqName, tree.pos), tree.pos);
                    invokeName = "invokeStatic";
                } else {
                    recv = _tmaker.at(tree.pos).Ident(_names._this);
                    invokeName = "invoke";
                }
            }

        } else if (Flags.isStatic(mi.msym)) {
            // convert to RT.invokeStatic("method", decl.class, args)
            String fqName = mi.msym.owner.getQualifiedName().toString();
            recv = classLiteral(mkFA(fqName, tree.pos), tree.pos);
            // TODO: this causes strangeness in weird places (See LibInterfaceTest)
            // recv = _tmaker.at(tree.pos).ClassLiteral((ClassSymbol)mi.msym.owner);
            invokeName = "invokeStatic";

        } else if (tree.meth instanceof JCFieldAccess) {
            // convert to RT.invoke("method", receiver, args)
            recv = ((JCFieldAccess)tree.meth).selected;
            invokeName = "invoke";

        } else {
            // if we're calling a method on an enclosing class, we need to supply "Enclosing.this"
            // instead of simply "this" for two reasons: it's more efficient to do the runtime
            // method lookup directly on its defining class, and we may be calling an enclosing
            // class's method as an argument to super(), where it is illegal to reference "this"
            JCExpression thisex;
            Type etype = _types.erasure(_env.enclClass.sym.type);
            Type otype = _types.erasure(mi.msym.owner.type);
            if (_types.isSubtype(etype, otype)) {
                recv = _tmaker.at(tree.pos).Ident(_names._this);
            } else {
                // we can't just use the method owner's type; the method may be declared in a
                // parent of our enclosing class and we need the type of our enclosing class in
                // our qualified this expression; for example:
                // class A { void foo (); }
                // class B extends A {
                //   class C { void bar () {
                //     foo(); // --> RT.invoke("foo", B.this); not A.this
                // }}}
                etype = getEnclosingSubtype(otype, etype);
                // TODO: will etype ever be null here? if so, what to do?
                recv = _tmaker.at(tree.pos).Select(typeToTree(etype, tree.pos), _names._this);
            }
            // convert to RT.invoke("method", this, args)
            invokeName = "invoke";
        }

        // if the method we're calling is varargs, we have to manually group the variable arguments
        // into an array so that we can create an array of the correct dynamic type; for
        // universally quantified methods, it is only possible at compile time to know the correct
        // dynamic type of the array
        if (mi.isValid() && ASTUtil.isVarArgs(mi.msym.flags())) {
            List<Type> ptypes = _resolver.instantiateType(_env, mi).asMethodType().argtypes;
            tree.args = groupVarArgs(ptypes, tree.args, mi.atypes, tree.pos);
        }

        tree.args = List.of(_tmaker.Literal(TreeInfo.name(tree.meth).toString()),
                            mkSigArgs(mi, tree.meth.pos), recv,
                            mkArray(_tmaker.Ident(_names.fromString("Object")), tree.args));
        tree.meth = mkRT(invokeName, tree.meth.pos);
        // Debug.log("APPLY " + mi.msym + " -> " + tree);
    }

    @Override public void visitSwitch (JCSwitch tree)
    {
        // we need to determine the static type of the selector and cast back to that to avoid a
        // complex transformation of switch into an equivalent set of if statements nested inside a
        // one loop for loop (to preserve 'break' semantics)
        Type type = _resolver.resolveType(_env, tree.selector, Kinds.VAL);

        // we have to look up the type *before* we transform the switch expression
        super.visitSwitch(tree);

        // we have to apply our checked cast *after* we transform the switch expression
        if (type == null) {
            Debug.warn("Can't resolve type for switch " + tree.selector);

        } else if (Flags.isEnum(type.tsym)) {
            tree.selector = cast(type, tree.selector);

        } else {
            // for integer types, we need to use asInt() rather than casting to Integer because
            // Java won't unbox and then coerce, whereas it will coerce; so if our switch
            // expression is int and the case constants are char, compilation will fail if we
            // promote the int to Integer
            tree.selector = callRT("asInt", tree.selector.pos, tree.selector);
        }
    }

    @Override public void visitIf (JCIf tree)
    {
        super.visitIf(tree);
        // we need to cast the if expression to boolean
        tree.cond = condCast(tree.cond);
    }

    @Override public void visitAssert (JCAssert tree)
    {
        super.visitAssert(tree);
        // we need to cast the assert expression to boolean
        tree.cond = condCast(tree.cond);
    }

    @Override public void visitConditional (JCConditional tree)
    {
        super.visitConditional(tree);
        // we need to cast the if expression to boolean
        tree.cond = condCast(tree.cond);
    }

    @Override public void visitDoLoop (JCDoWhileLoop tree)
    {
        super.visitDoLoop(tree);
        // we need to cast the while expression to boolean
        tree.cond = condCast(tree.cond);
    }

    @Override public void visitWhileLoop (JCWhileLoop tree)
    {
        super.visitWhileLoop(tree);
        // we need to cast the while expression to boolean
        tree.cond = condCast(tree.cond);
    }

    @Override public void visitForLoop (JCForLoop tree)
    {
        super.visitForLoop(tree);
        // "for (;;)" will have null condition
        if (tree.cond != null) {
            // we need to cast the for condition expression to boolean
            tree.cond = condCast(tree.cond);
        }
    }

    @Override public void visitForeachLoop (JCEnhancedForLoop tree)
    {
        super.visitForeachLoop(tree);

        // rewrite the foreach loop as: foreach (iter : RT.asIterable(expr))
        tree.expr = callRT("asIterable", tree.expr.pos, tree.expr);
    }

    @Override public void visitTry (JCTry tree)
    {
        super.visitTry(tree);

        // insert an inner try/catch that catches WrappedException, dynamically checks whether its
        // cause is the caught type and casts and rethrows the cause if so
        if (!tree.catchers.isEmpty()) {
            Name cvname = _names.fromString(tree.catchers.head.param.name + "$W");
            JCCatch catcher = _tmaker.Catch(
                _tmaker.VarDef(_tmaker.Modifiers(0L), cvname,
                               mkFA("org.ductilej.runtime.WrappedException", 0), null),
                _tmaker.Block(0L, List.of(unwrapExns(cvname, tree.catchers))));
            tree.body = _tmaker.Block(
                0L, List.<JCStatement>of(_tmaker.Try(tree.body, List.of(catcher), null)));
        }
    }

    @Override public void visitThrow (JCThrow tree) {
        // if the throw expression is not "new Something" or "(SomeExn)expr", we need to insert a
        // dynamic cast back to the expected type of the throw to preserve exception flow analysis
        Type etype = null;
        if (tree.expr.getTag() != JCTree.NEWCLASS && tree.expr.getTag() != JCTree.TYPECAST) {
            etype = _resolver.resolveType(_env, tree.expr, Kinds.VAL);
        }
        super.visitThrow(tree);
        if (etype != null) {
            tree.expr = cast(etype, tree.expr);
        }
    }

    @Override public void visitTypeCast (JCTypeCast tree)
    {
        super.visitTypeCast(tree);

        String path = path();
        if (!(tree.clazz instanceof JCExpression)) {
            Debug.warn("Got cast to non-JCExpression clazz?", "tree", tree);
            // just leave it as is and hope for the best

        } else if (path.endsWith("Switch.Case")) {
            // leave casts in switch case expressions alone, they are necessary to turn bytes into
            // ints and so forth

        } else if (path.endsWith("Block.Throw")) {
            // if we're casting an expression to an exception type, turn it into a checked cast
            result = checkedCast((JCExpression)tree.clazz, tree.expr);

        } else if (tree.expr.getTag() == JCTree.LITERAL) {
            // if the expression being casted is a literal, leave the cast in place; that's a
            // conversion, not a type cast, and we need to preserve it

        } else if (tree.clazz.getTag() == JCTree.TYPEIDENT) {
            // if the cast is to a primitive type, we need to emulate any coercions that would take
            // place as a result of such a cast
            switch (((JCPrimitiveTypeTree)tree.clazz).typetag) {
            case TypeTags.BYTE:
            case TypeTags.SHORT:
            case TypeTags.INT:
            case TypeTags.LONG:
            case TypeTags.CHAR:
            case TypeTags.FLOAT:
            case TypeTags.DOUBLE:
                result = callRT("coerce", tree.pos,
                                classLiteral((JCExpression)tree.clazz, tree.pos), tree.expr);
                break;

            case TypeTags.BOOLEAN: // no representation change needed for boolean
            case TypeTags.VOID: // this should never appear in Java code AFAIK
                result = tree.expr;
                break;

            default:
                throw new AssertionError("Unknown primitive type " + tree.clazz);
            }

        } else {
            // TODO: if the cast expression contains type variables, we need to compute their upper
            // bound and note a cast to that upper bound
            result = tree.expr;
//             result = callRT("noteCast", tree.pos,
//                             classLiteral((JCExpression)tree.clazz, tree.pos), tree.expr);
        }
    }

    @Override public void visitIndexed (JCArrayAccess tree)
    {
        super.visitIndexed(tree);

        // rewrite the array dereference as: RT.atIndex(array, index)
        result = callRT("atIndex", tree.pos, tree.indexed, tree.index);
    }

    @Override public void visitAssign (JCAssign tree)
    {
        // we don't call super as we may need to avoid translating the LHS

        // if we're in an annotation declaration, don't touch anything
        if (path().endsWith(".Annotation")) {
            result = tree;
        } else {
            // if the RHS is a constant expression that is wider than the LHS, we must implicitly
            // narrow it; if it is any expression that is narrower, we must implicitly widen it
            Type ltype = _resolver.resolveType(_env, tree.lhs, Kinds.VAR);
            Type rtype = ltype.isPrimitive() ?
                _resolver.resolveType(_env, tree.rhs, Kinds.VAL) : null;
            // we have to do the above type resolution *before* we translate the rhs as it may make
            // in-place modifications to the AST
            JCExpression trhs = translate(tree.rhs);
            // TODO: only implicitly narrow when RHS is a constant expr?
            if (ltype != null && rtype != null && ltype.isPrimitive() && ltype.tag != rtype.tag) {
                trhs = callRT("coerce", tree.rhs.pos, classLiteral(ltype, tree.rhs.pos), trhs);
            }
            result = mkAssign(tree.lhs, trhs, tree.pos);
        }
    }

    @Override public void visitBlock (JCBlock tree)
    {
        Env<DetypeContext> oenv = _env;
        if (_env.info.scope.owner.kind == Kinds.TYP) {
            // block is a static or instance initializer; let the owner of the environment be a
            // freshly created BLOCK-method
            _env = oenv.dup(tree, oenv.info.dup(oenv.info.scope.dupUnshared()));
            _env.info.scope.owner = new MethodSymbol(tree.flags | Flags.BLOCK, _names.empty, null,
                                                     oenv.info.scope.owner);
            // if ((tree.flags & STATIC) != 0) localEnv.info.staticLevel++;
            super.visitBlock(tree);
        } else {
            // create a new local environment with a local scope
            _env = oenv.dup(tree, oenv.info.dup(oenv.info.scope.dup()));
            super.visitBlock(tree);
            _env.info.scope.leave(); // needed (I think) because we didn't dupUnshared()
        }
        _env = oenv;
    }

    protected Detype (Context ctx)
    {
        ctx.put(DETYPE_KEY, this);
        _resolver = Resolver.instance(ctx);
        _types = Types.instance(ctx);
        _enter = Enter.instance(ctx);
        _memberEnter = MemberEnter.instance(ctx);
        _attr = Attr.instance(ctx);
        // _names = Name.Table.instance(ctx);
        _names = Names.instance(ctx);
        _syms = Symtab.instance(ctx);
        _annotate = Annotate.instance(ctx);
        _lint = Lint.instance(ctx);
        _rootmaker = TreeMaker.instance(ctx);
        _log = Log.instance(ctx);

        _mainName = _names.fromString("main");
        _readObjectName = _names.fromString("readObject");
        _writeObjectName = _names.fromString("writeObject");

        // a class that will enclose all outer classes
        _predefClassDef = _rootmaker.ClassDef(
            _rootmaker.Modifiers(Flags.PUBLIC),
            _syms.predefClass.name, null, null, null, null);
        _predefClassDef.sym = _syms.predefClass;

        // a wildcard class type for use in various places
        _wildcardClass = toArrayElementType(_syms.classType);
    }

    protected boolean inLibraryOverrider ()
    {
        // if we're not in a method, we can't be in a library overrider
        if (_env.enclMethod == null) {
            return false;
        }

        // if these are magic serialization methods (readObject, writeObject), they are considered
        // library overriders (they must not be detyped)
        Name mname = _env.enclMethod.getName();
        int pcount = _env.enclMethod.params.size();
        if (mname == _readObjectName && pcount == 1) {
            return String.valueOf(_env.enclMethod.params.head.sym.type).equals(OIN_STREAM);

        } else if (mname == _writeObjectName && pcount == 1) {
            return String.valueOf(_env.enclMethod.params.head.sym.type).equals(OOUT_STREAM);

        // "public static void main (String[] args)" must also remain undetyped
        } else if (mname == _mainName && pcount == 1) {
            return (_env.enclMethod.sym.flags() & PUBSTATIC) == PUBSTATIC &&
                String.valueOf(_env.enclMethod.params.head.sym.type).equals(STRING_ARRAY);
        }

        // other serialization methods exist: readObjectNoData, readReplace, writeReplace,
        // readResolve; fortunately their signatures do not change under detyping

        // otherwise we need to actually look at the type hierarchy
        return ASTUtil.isLibraryOverrider(_types, _env.enclMethod.sym);
    }

    protected boolean inStatic ()
    {
        return _env.info.inChainedCons || (_env.enclMethod == null) ||
            (_env.enclMethod.mods != null && (_env.enclMethod.mods.flags & Flags.STATIC) != 0);
    }

    protected boolean isConstDecl (JCVariableDecl tree)
    {
        // if the type of the RHS is not a primitive...
        if (tree.vartype.getTag() != JCTree.TYPEIDENT) {
            // ...then the only other possible const expression is a string
            Type vtype = _resolver.resolveType(_env, tree.vartype, Kinds.TYP);
            // if we could not resolve the type of the RHS, it's definitely not a string literal
            if (vtype == null || !_types.isSameType(vtype, _syms.stringType)) {
                return false;
            }
        }

        // it must be explicitly declared final or be an interface field member (which is
        // implicitly considered final)
        if (!ASTUtil.isFinal(tree.mods) && (_env.enclClass.sym.flags() & Flags.INTERFACE) == 0) {
            return false;
        }

        // finally the initializer must be a constant expression
        return isConstantExpr(tree.init);
    }

    protected boolean isConstantExpr (JCTree expr)
    {
        if (expr == null) {
            return false;
        } else if (expr.getTag() == JCTree.LITERAL) {
            return true;
        } else if (expr.getTag() == JCTree.IDENT) {
            Symbol sym = _resolver.resolveSymbol(_env, expr, Kinds.VAL);
            return (sym instanceof Symbol.VarSymbol) &&
                ((Symbol.VarSymbol)sym).getConstantValue() != null;
        } else if (expr instanceof JCParens) {
            return isConstantExpr(((JCParens)expr).expr);
        } else if (expr instanceof JCUnary) {
            return isConstantExpr(((JCUnary)expr).arg);
        } else if (expr instanceof JCBinary) {
            return isConstantExpr(((JCBinary)expr).lhs) &&
                isConstantExpr(((JCBinary)expr).rhs);
        } else if (expr instanceof JCTypeCast) {
            return isConstantExpr(((JCTypeCast)expr).expr);
        } else {
            // Debug.temp("Not const? " + expr, "type", expr.getClass().getName());
            return false;
        }
    }

    protected boolean isUndetypableMethod (JCMethodDecl meth)
    {
        Name mname = meth.getName();
        int pcount = meth.params.size();

        // avoid detyping constructors of classes that extend junit.framework.TestCase that take a
        // single String argument
        for (Type t = _env.enclClass.sym.getSuperclass(); t != _syms.objectType && t.tsym != null;
             t = ((ClassSymbol)t.tsym).getSuperclass()) {
            if (mname == _names.init && pcount == 1 &&
                String.valueOf(t).equals(JUNIT_TESTCASE) &&
                String.valueOf(meth.params.head.vartype).equals("String")) {
                return true;
            }
        }

        return false;
    }

    protected JCMethodInvocation unop (int pos, Tree.Kind op, JCExpression arg)
    {
        return _tmaker.at(pos).Apply(
            null, mkFA(Unop.class.getName() + "." + op + ".invoke", pos), List.of(arg));
    }

    protected JCMethodInvocation binop (int pos, Tree.Kind op, JCExpression lhs, JCExpression rhs)
    {
        return _tmaker.at(pos).Apply(
            null, mkFA(Binop.class.getName() + "." + op + ".invoke", pos), List.of(lhs, rhs));
    }

    /**
     * Wraps a conditional expression in a call to {@link RT#asBoolean} iff the expression is not a
     * literal that might be influencing static analysis of the underyling conditional.
     */
    protected JCExpression condCast (JCExpression expr)
    {
        switch (expr.getTag()) {
        case JCTree.AND: // we don't turn && and || expressions into reflective calls
        case JCTree.OR:  // to preserve short-circuit behavior, so we need not to wrap them here so
                         // that the compiler can account for short-circuit behavior in definite
                         // assignment analysis
        case JCTree.LITERAL: return expr;
        // TODO: special handling for static final constants?
        default: return callRT("asBoolean", expr.pos, expr);
        }
    }

    protected JCMethodInvocation checkedCast (JCExpression clazz, JCExpression expr) {
        return callRT("checkedCast", expr.pos, classLiteral(clazz, expr.pos), expr);
    }

    protected JCMethodInvocation typeVarCast (JCExpression clazz, JCExpression expr, Type ptype) {
        JCMethodInvocation inv = callRT(
            "typeVarCast", expr.pos, classLiteral(clazz, expr.pos), expr);
        // we specify the return type of the dynamic cast explicitly so that we can supply the
        // concrete upper bound as the runtime class but still retain the type variable as our
        // static return type, e.g.: T val = RT.<T>checkedCast(Object.class, oval)
        inv.typeargs = List.<JCExpression>of(typeToTree(ptype, expr.pos));
        return inv;
    }

    protected JCMethodInvocation cast (Type type, JCExpression expr)
    {
        Type etype = _types.erasure(type);
        JCExpression clazz = typeToTree(etype, expr.pos);
        return !_types.isSameType(etype, type) ?
            typeVarCast(clazz, expr, type) : checkedCast(clazz, expr);
    }

    protected List<JCExpression> castIntList (List<JCExpression> list)
    {
        return list.isEmpty() ? List.<JCExpression>nil() : 
            castIntList(list.tail).prepend(
                checkedCast(_tmaker.Ident(_names.fromString("Integer")), list.head));
    }

    protected List<JCExpression> castList (List<Type> params, List<JCExpression> list)
    {
        return list.isEmpty() ? List.<JCExpression>nil() :
            castList(params.tail, list.tail).prepend(cast(params.head, list.head));
    }

    protected List<JCExpression> castList (Type type, List<JCExpression> list)
    {
        if (list.isEmpty()) {
            return List.<JCExpression>nil();
        }
        if (_types.isSameType(type, _syms.objectType)) {
            return list;
        }
        return castList(type, list.tail).prepend(cast(type, list.head));
    }

    protected JCMethodInvocation callRT (String method, int pos, JCExpression... args) {
        return _tmaker.at(pos).Apply(null, mkRT(method, pos), List.from(args));
    }

    protected JCMethodInvocation callRT (String method, int pos, List<JCExpression> args) {
        return _tmaker.at(pos).Apply(null, mkRT(method, pos), args);
    }

    protected JCExpression mkRT (String method, int pos) {
        return mkFA(RT.class.getName() + "." + method, pos);
    }

    protected JCExpression mkFA (String fqName, int pos) {
        int didx = fqName.lastIndexOf(".");
        if (didx == -1) {
            return _tmaker.at(pos).Ident(_names.fromString(fqName)); // simple identifier
        } else {
            return _tmaker.at(pos).Select(mkFA(fqName.substring(0, didx), pos), // nested FA expr
                                          _names.fromString(fqName.substring(didx+1)));
        }
    }

    // NOTE: lhs must not have been translated, rhs must have been translated
    protected JCExpression mkAssign (JCExpression lhs, JCExpression rhs, int pos)
    {
        switch (lhs.getTag()) {
        case JCTree.INDEXED: {
            JCArrayAccess aa = (JCArrayAccess)lhs;
            return callRT("assignAt", pos, translate(aa.indexed), translate(aa.index), rhs);
        }

        case JCTree.SELECT: {
            JCFieldAccess fa = (JCFieldAccess)lhs;
            // if the expression is "this.something = ...", we want to avoid turning the lhs into a
            // reflective assignment; we want to preserve definite assignment in constructors
            if ((fa.selected instanceof JCIdent && ((JCIdent)fa.selected).name == _names._this) ||
                // TODO: in this case we should perform "assignStatic"
                _resolver.isStaticSite(_env, fa.selected)) {
                // if the field being assigned is defined in a library class, we need to cast the
                // right hand side back to the type 
                return _tmaker.at(pos).Assign(lhs, maybeCastRHS(lhs, rhs));
                // TODO: resolve the field on the lhs and only preserve definite assignment if it
                // exists and it's a final field
            } else {
                return callRT("assign", pos, translate(fa.selected),
                              _tmaker.Literal(fa.name.toString()), rhs);
            }
        }

        case JCTree.IDENT:
            // if the LHS is a field (with implicit this), we need to check whether it is defined
            // in a library class; if so we to dynamically cast the RHS to the correct static type
            return _tmaker.at(pos).Assign(translate(lhs), maybeCastRHS(lhs, rhs));

        // TODO: we need to handle (foo[ii]) = 1 (and maybe others?)
        default:
            return _tmaker.at(pos).Assign(translate(lhs), rhs);
        }
    }

    protected JCExpression mkArray (JCExpression type, List<JCExpression> elems)
    {
        return _tmaker.NewArray(type, List.<JCExpression>nil(), elems);
    }

    protected JCExpression mkSigArgs (Resolver.MethInfo mi, int pos)
    {
        if (mi.isValid()) {
            // create our 'new Class<?>[] { Arg.class, ... }' array
            return mkArray(typeToTree(_wildcardClass, pos),
                           classLiterals(mi.msym.type.getParameterTypes(), pos));
        } else {
            // if we failed to resolve the method, supply null for the argument types; this lets
            // the runtime know that it needs to do the more expensive (and potentially
            // semantically non-equivalent) method resolution based on runtime argument types
            return _tmaker.Literal(TypeTags.BOT, null);
        }
    }

    protected JCExpression maybeCastRHS (JCExpression lhs, JCExpression rhs)
    {
        Symbol sym = _resolver.resolveSymbol(_env, lhs, Kinds.VAR);
        if (ASTUtil.isLibrary(sym)) {
            return cast(_resolver.resolveType(_env, lhs, Kinds.VAR), rhs);
        } else {
            return rhs;
        }
    }

    protected JCExpression classLiteral (JCExpression expr, int pos)
    {
        return _tmaker.at(pos).Select(expr, _names._class);
    }

    protected JCExpression classLiteral (Type type, int pos)
    {
        // because we're generating a class literal from a type, we always want its erasure
        return _tmaker.at(pos).Select(typeToTree(_types.erasure(type), pos), _names._class);
    }

    protected List<JCExpression> classLiterals (List<Type> types, int pos)
    {
        return types.isEmpty() ? List.<JCExpression>nil() :
            classLiterals(types.tail, pos).prepend(classLiteral(types.head, pos));
    }

    protected JCExpression typeToTree (Type type, final int pos)
    {
        // note: we use _rootmaker here instead of _tmaker because _tmaker will try to generate
        // unqualified names for classes that are imported, but this opens us up to name collisions
        // when we are resolving the name of a type that does not normally appear in the source
        // file but we happen to need, and said type happens to be shadowed by a local type name;
        // _tmaker will assume the imported name can be referenced unqualified, but that will
        // resolve to the incorrect shadowing type
        JCExpression expr = _rootmaker.at(pos).Type(type);

        // there's a pesky bug in TreeMaker.Type that puts java.lang.Object as the "inner" field of
        // a JCWildcard for unbound declarations (i.e. Class<?>) which later causes havoc to be
        // wreaked inside the compiler because it expects inner to be null
        expr.accept(new TreeScanner() {
            public void visitWildcard (JCWildcard tree) {
                switch (tree.kind.kind) {
                case UNBOUND: tree.inner = null; break;
                }
            }
        });

        // there's another pesky bug in TreeMaker.Type that creates ASTs that look like
        // .com.foo.bar.Baz instead of com.foo.bar.Baz, so we work around that here
        expr.accept(new TreeScanner() {
            public void visitSelect (JCFieldAccess tree) {
                if (tree.selected instanceof JCFieldAccess) {
                    JCFieldAccess stree = (JCFieldAccess)tree.selected;
                    if (stree.selected instanceof JCIdent &&
                        TreeInfo.name(stree.selected) == _names.empty) {
                        tree.selected = _tmaker.at(pos).Ident(stree.name);
                    }
                }
                super.visitSelect(tree);
            }
        });

        return expr;
    }

    protected List<JCExpression> typesToTree (List<Type> types, int pos)
    {
        return types.isEmpty() ? List.<JCExpression>nil() :
            typesToTree(types.tail, pos).prepend(typeToTree(types.head, pos));
    }

    protected JCStatement unwrapExns (Name cvname, List<JCCatch> catchers)
    {
        if (catchers.isEmpty()) {
            return _tmaker.Throw(_tmaker.Ident(cvname));
        }

        // create our new throw expression (exn.getCause())
        Name gcname = _names.fromString("getCause");
        JCExpression texpr = _tmaker.Apply(List.<JCExpression>nil(),
                                           _tmaker.Select(_tmaker.Ident(cvname), gcname),
                                           List.<JCExpression>nil());

        // if the type we're throwing is not already Throwable, then cast it
        String vtype = catchers.head.param.vartype.toString();
        if (!vtype.equals("java.lang.Throwable") && !vtype.equals("Throwable")) {
            texpr = _tmaker.TypeCast((JCTree)catchers.head.param.vartype.clone(), texpr);
        }

        // finally wrap this all up in:
        // if (exn.getCause() instanceof E) throw (E)exn.getCause() else ...
        return _tmaker.If(
            _tmaker.TypeTest(
                _tmaker.Apply(List.<JCExpression>nil(),
                              _tmaker.Select(_tmaker.Ident(cvname), gcname),
                              List.<JCExpression>nil()),
                (JCTree)catchers.head.param.vartype.clone()),
            _tmaker.Throw(texpr),
            unwrapExns(cvname, catchers.tail));
    }

    protected boolean isSuperMethodCall (JCMethodInvocation tree)
    {
        return (tree.meth instanceof JCFieldAccess) &&
            (TreeInfo.name(((JCFieldAccess)tree.meth).selected) == _names._super);
    }

    protected List<JCVariableDecl> toTypeArgs (List<JCVariableDecl> params)
    {
        if (params.isEmpty()) {
            return List.nil();
        }
        // inherit the flags of our value carrying parameter (plus final)
        long flags = params.head.mods.flags | Flags.FINAL;
        // if we inherited the varargs flag, we need to turn the varargs flag off on our value
        // carrying parameter so that it can accept any argument (rather than Object[])
        if (ASTUtil.isVarArgs(flags)) {
            params.head.mods.flags &= ~Flags.VARARGS;
        }
        return toTypeArgs(params.tail).prepend(
            _tmaker.VarDef(_tmaker.Modifiers(flags),
                           params.head.name.append(_names.fromString(TP_SUFFIX)),
                           params.head.vartype, null));
    }

    protected List<JCExpression> addManglingArgs (
        Symbol msym, List<Type> ptypes, List<JCExpression> args, List<Type> atypes, int pos)
    {
        // if the method is varargs, we need to insert an array creation expression wrapping up the
        // variable arguments because they're no longer at the end (whee!)
        if (ASTUtil.isVarArgs(msym.flags())) {
            args = groupVarArgs(ptypes, args, atypes, pos);
        }
        // now we can append type carrying arguments to the grouped arglist
        return args.appendList(toTypedNulls(ptypes, args));
    }

    protected List<JCExpression> groupVarArgs (List<Type> ptypes, List<JCExpression> args,
                                               List<Type> atypes, int pos)
    {
        // skip the non-varargs arguments
        if (!ptypes.tail.isEmpty()) {
            return groupVarArgs(ptypes.tail, args.tail, atypes.tail, pos).prepend(args.head);
        }

        // if we have an array argument of the correct type in the final position, it should not be
        // wrapped, but rather passed straight through
        if (!atypes.isEmpty() && atypes.tail.isEmpty() &&
            // any checked or unchecked subtype of the varargs array type should be passed through
            // directly as the varargs array rather than doubly wrapped
            _types.isSubtypeUnchecked(atypes.head, ptypes.head)) {
            return args;
        }

        // if the varargs argument is parameterized (naughty programmer), we have to erase it to
        // its upper bound and fill any type parameters with unbounded wildcards (?) to convert it
        // to a legal array element type
        Type atype = toArrayElementType(((Type.ArrayType)ptypes.head).elemtype);
        return List.<JCExpression>of(mkArray(typeToTree(atype, pos), castList(atype, args)));
    }

    protected List<JCExpression> toTypedNulls (List<Type> types, List<JCExpression> args)
    {
        if (types.isEmpty()) {
            return List.nil();
        }
        return toTypedNulls(types.tail, args.tail).prepend(toTypedNull(types.head, args.head));
    }

    /**
     * Converts a type to an expression that will evaluate to that type but carry no (meaningful)
     * value. For reference types, this is the equivalent of "(T)null" except we don't use that
     * exact expression because "(C<T>)null" results in a pesky warning from the compiler that we'd
     * rather avoid. For primitive types, we insert the appropriate literal for 0, or false.
     */
    protected JCExpression toTypedNull (Type type, JCExpression arg)
    {
        switch (type.tag) {
        case TypeTags.BOOLEAN: // yes, this uses 0 well
        case TypeTags.BYTE:
        case TypeTags.CHAR:
        case TypeTags.SHORT:
        case TypeTags.INT:
        case TypeTags.LONG:
        case TypeTags.FLOAT:
        case TypeTags.DOUBLE:
            return _tmaker.Literal(type.tag, 0); // TODO
        default:
            return _tmaker.at(arg.pos).TypeCast(
                typeToTree(type, arg.pos), _tmaker.Literal(TypeTags.BOT, null));
        }
    }

    protected Type toArrayElementType (Type type)
    {
        return (type.tag != TypeTags.CLASS) ? _types.erasure(type) :
            new Type.ClassType(toArrayElementType(type.getEnclosingType()),
                               wildcardify(type.getTypeArguments()), type.tsym);
    }

    protected Type wildcardify (Type type)
    {
        switch (type.tag) {
        case TypeTags.TYPEVAR:
        case TypeTags.WILDCARD:
        case TypeTags.CLASS:
            return new Type.WildcardType(_syms.objectType, BoundKind.UNBOUND, _syms.boundClass);
        default:
            return type;
        }
    }

    protected List<Type> wildcardify (List<Type> types)
    {
        return types.isEmpty() ? List.<Type>nil() :
            wildcardify(types.tail).prepend(wildcardify(types.head));
    }

    protected List<JCTree> removeMethodDefs (List<JCTree> defs)
    {
        if (defs.isEmpty()) {
            return List.nil();
        } else if (defs.head.getTag() == JCTree.METHODDEF) {
            return removeMethodDefs(defs.tail);
        } else {
            return removeMethodDefs(defs.tail).prepend(defs.head);
        }
    }

    protected List<JCAnnotation> removeOverrideAnnotation (List<JCAnnotation> anns)
    {
        if (anns.isEmpty()) {
            return List.nil();
        } else if (anns.head.annotationType.toString().equals("Override")) { // TODO: unhack
            return removeOverrideAnnotation(anns.tail);
        } else {
            return removeOverrideAnnotation(anns.tail).prepend(anns.head);
        }
    }

    protected Type getEnclosingSubtype (Type otype, Type etype)
    {
        do {
            Type eetype = etype.getEnclosingType();
            if (eetype.tag >= TypeTags.NONE) {
                return null;
            }
            etype = _types.erasure(etype.getEnclosingType());
        } while (!_types.isSubtype(etype, otype));
        return etype;
    }

    protected boolean isVarImport (JCImport tree)
    {
        Scope.Entry e = _env.toplevel.namedImportScope.lookup(TreeInfo.name(tree.qualid));
        for (; e.scope != null; e = e.next()) {
            if (e.sym.kind == Kinds.VAR) {
                return true;
            }
        }
        return false;
    }

    protected static String what (JCTree node)
    {
        if (node == null) {
            return "null";
        } else if (node instanceof JCIdent) {
            return node.getClass().getSimpleName() + "[" + node + "/" + ((JCIdent)node).sym + "]";
        } else {
            return node.getClass().getSimpleName() + "[" + node + "]";
        }
    }

    protected static String what (Symbol sym)
    {
        return (sym == null) ? "null" : (sym + "/" + sym.getClass().getSimpleName());
    }

    protected Env<DetypeContext> _env;
    protected TreeMaker _tmaker;

    protected JCTree.JCClassDecl _predefClassDef;
    protected Name _mainName, _readObjectName, _writeObjectName;
    protected Type _wildcardClass;

    protected Resolver _resolver;
    protected Types _types;
    // protected Name.Table _names;
    protected Names _names;
    protected Enter _enter;
    protected MemberEnter _memberEnter;
    protected Attr _attr;
    protected Symtab _syms;
    protected Annotate _annotate;
    protected Lint _lint;
    protected TreeMaker _rootmaker;
    protected Log _log;

    protected static final String TP_SUFFIX = "$T";
    protected static final Context.Key<Detype> DETYPE_KEY = new Context.Key<Detype>();

    protected static final int PUBSTATIC = Flags.PUBLIC | Flags.STATIC;

    // type names needed by inLibraryOverrider()
    protected static final String OIN_STREAM = "java.io.ObjectInputStream";
    protected static final String OOUT_STREAM = "java.io.ObjectOutputStream";
    protected static final String STRING_ARRAY = "java.lang.String[]";

    // type names needed by isUndetypableMethod()
    protected static final String JUNIT_TESTCASE = "junit.framework.TestCase";

    // whether definite assignment relaxation is enabled
    protected static final boolean RELAX_DEFASSIGN = true;
}
