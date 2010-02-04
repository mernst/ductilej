//
// $Id$

package org.typelessj.detyper;

import java.util.Set;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Flags;
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
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name; // Name.Table -> Names in OpenJDK
import com.sun.tools.javac.util.Names;

import org.typelessj.runtime.Debug;
import org.typelessj.runtime.RT;
import org.typelessj.runtime.Transformed;
import org.typelessj.util.ASTUtil;
import org.typelessj.util.PathedTreeTranslator;

/**
 * Performs the actual detyping.
 */
public class Detype extends PathedTreeTranslator
{
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

        // TODO: everything in AttrContext is helpfully package protected, yay!
        // copy over detype context parts that are useful to attr context
        // aenv.info.scope = env.info.scope;

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
            tree.accept(this);
        } finally {
            _env = oenv;
        }
    }

    @Override public void visitTopLevel (JCCompilationUnit tree) {
        _tmaker = _rootmaker.forToplevel(tree);

        // Debug.log("Named-import scope", "scope", tree.namedImportScope);
        // Debug.log("Star-import scope", "scope", tree.starImportScope);
        super.visitTopLevel(tree);
    }

    @Override public void visitClassDef (JCClassDecl tree) {
        Debug.log("Entering class '" + tree.name + "'", "sym", tree.sym);

        // for local and anonymous classes, we need to create a fake class symbol and do our own
        // partial Enter on it to obtain enough symbol information to keep everything working
        boolean fakeEntered = false;
        if (tree.sym == null) {
            Symbol encsym = (_env.enclMethod == null) ? _env.enclClass.sym : _env.enclMethod.sym;
            ClassSymbol csym = new ClassSymbol(0, tree.name, encsym);
            csym.members_field = new Scope(csym); // TODO: do we want a next?
// TODO: enter methods, etc.
//             tree.accept(new TreeScanner() {
//                 @Override public void visitMethodDef (JCMethodDecl tree) {
//                 }
//             });
            tree.sym = csym;
            fakeEntered = true;
        }

        // note the environment of the class we're processing
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(tree.sym.members_field.dupUnshared()));
        _env.enclClass = tree;
        _env.outer = oenv;

        if (tree.name != _names.empty) {
            // add our @Transformed annotation to the AST
            JCAnnotation a = _tmaker.at(tree.pos).Annotation(
                mkFA(Transformed.class.getName(), tree.pos), List.<JCExpression>nil());
            tree.mods.annotations = tree.mods.annotations.prepend(a);

            if (!fakeEntered) {
                // since the annotations AST has already been resolved into type symbols, we have
                // to manually add a type symbol for annotation to the ClassSymbol
                tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                    Backdoor.enterAnnotation(_annotate, a, _syms.annotationType,
                                             _enter.getEnv(tree.sym)));
            }
        }

        super.visitClassDef(tree);
        _env = oenv;
        if (fakeEntered) {
            tree.sym = null;
        }

        Debug.log("Leaving class " + tree.name);
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
        Debug.log("Visiting method def", "name", tree.name);

        // create a local environment for this method definition
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(oenv.info.scope.dupUnshared()));
        _env.enclMethod = tree;
        _env.info.scope.owner = tree.sym;

        // if we're not in a library overrider, prepare our type-carrying arguments (before we call
        // super which will erase our argument's types)
        boolean isLib = inLibraryOverrider();
        // TEMP: don't sigmangle constructors
        List<JCVariableDecl> sigargs = isLib || (tree.name == _names.init) ?
            List.<JCVariableDecl>nil() : toTypeArgs(tree.params);

        // now we can call super and translate our children
        super.visitMethodDef(tree);

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
        }

        // restore our previous environment
        _env = oenv;
    }

    @Override public void visitVarDef (JCVariableDecl tree) {
        super.visitVarDef(tree);

        // var symbols for member-level variables are already entered, we just want to handle
        // formal parameters and local variable declarations
        if (_env.tree.getTag() != JCTree.CLASSDEF) {
            // create a placeholder VarSymbol for this variable so that we can use it later
            // during some simple name resolution
            Type vtype = _resolver.resolveAsType(_env, tree.vartype, true);
            _env.info.scope.enter(new VarSymbol(0, tree.name, vtype, _env.info.scope.owner));
        }

        String path = path();
        // we don't want to detype the param(s) of a catch block
        if (!path.contains(".Catch") &&
            // nor the arguments of a library
            !(path.contains(".MethodDef.params") && inLibraryOverrider()) &&
            // nor static final members (because they may be used in a case statement in which case
            // they must be primitive constants or Enum values); NOTE: because of that we may want
            // to only avoid detyping static final primitive and Enum fields
            !ASTUtil.isStaticFinal(tree.mods)) {
//             Debug.log("Transforming vardef", "mods", tree.mods, "name", tree.name,
//                      "vtype", what(tree.vartype), "init", tree.init,
//                      "sym", ASTUtil.expand(tree.sym));
            tree.vartype = _tmaker.Ident(_names.fromString("Object"));
            if ((tree.mods.flags & Flags.VARARGS) != 0) {
                tree.vartype = _tmaker.TypeArray(tree.vartype);
            }

        } else {
            Debug.log("Not transforming", "def", tree, "path", path, "isLib", inLibraryOverrider());
        }
    }

    @Override public void visitReturn (JCReturn tree) {
        super.visitReturn(tree);

        // if we're in a method whose signature cannot be transformed, we must cast the result of
        // the return type back to the static method return type
        if (tree.expr != null && inLibraryOverrider()) {
            tree.expr = checkedCast(_env.enclMethod.restype, tree.expr);
        }
    }

    @Override public void visitUnary (JCUnary tree) {
        // we don't call super because mkAssign needs the untranslated expr for ++ and --

        JCExpression expr;
        JCLiteral one = _tmaker.Literal(TypeTags.INT, 1);
        switch (tree.getKind()) {
        case PREFIX_INCREMENT:  // ++i -> (i = i + 1)
        case POSTFIX_INCREMENT: // i++ -> ((i = i + 1) - 1)
            expr = mkAssign(tree.arg, binop(tree.pos, Tree.Kind.PLUS, translate(tree.arg), one),
                            tree.pos);
            if (tree.getKind() == Tree.Kind.POSTFIX_INCREMENT) {
                expr = binop(tree.pos, Tree.Kind.MINUS, expr, one);
            }
            break;

        case PREFIX_DECREMENT:  // --i -> (i = i - 1)
        case POSTFIX_DECREMENT: // i-- -> ((i = i - 1) + 1)
            expr = mkAssign(tree.arg, binop(tree.pos, Tree.Kind.MINUS, translate(tree.arg),
                                            _tmaker.Literal(TypeTags.INT, 1)), tree.pos);
            if (tree.getKind() == Tree.Kind.POSTFIX_DECREMENT) {
                expr = binop(tree.pos, Tree.Kind.PLUS, expr, one);
            }
            break;

        default:
            expr = unop(tree.pos, tree.getKind(), translate(tree.arg));
            break;
        }

        // Debug.log("Rewrote unop: " + tree + " -> " + expr);
        result = expr;
    }

    @Override public void visitBinary (JCBinary tree) {
        super.visitBinary(tree);

        JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
        result = callRT("binop", tree.pos, opcode, tree.lhs, tree.rhs);
        // Debug.log("Rewrote binop", "kind", tree.getKind(), "tp", tree.pos, "ap", opcode.pos);
    }

    @Override public void visitAssignop (JCAssignOp tree) {
        // we don't call super because mkAssign needs the untranslated LHS

        // we create this temporary JCBinary so that we can call bop.getKind() below which calls
        // into non-public code that would otherwise be annoying to call
        JCExpression bop = _tmaker.Binary(tree.getTag() - JCTree.ASGOffset, tree.lhs, tree.rhs);
        bop = binop(tree.pos, bop.getKind(), translate(tree.lhs), translate(tree.rhs));
        // TODO: this a problem wrt evaluating the LHS more than once, we probably need to do
        // something painfully complicated
        result = mkAssign(tree.lhs, bop, tree.pos);
        // Debug.log("Rewrote assignop", "kind", tree.getKind(), "into", result);
    }

    @Override public void visitNewClass (JCNewClass tree) {
//         Debug.log("Class instantiation", "typeargs", tree.typeargs, "class", what(tree.clazz),
//                   "args", tree.args);

        // if we see an anonymous inner class declaration, resolve the type of the to-be-created
        // class, we need this in inLibraryOverrider() for our approximation approach
        Symbol oanonp = _env.info.anonParent;
        if (tree.def != null) {
            Type atype = _resolver.resolveAsType(_env, tree.clazz, false);
            if (atype == null) {
                Debug.warn("Unable to resolve type of anon inner parent", "name", tree.clazz);
            } else {
                _env.info.anonParent = atype.tsym;
            }
        }
        super.visitNewClass(tree);

        // enums are already desugared somewhat by the time we are called, so the AST looks
        // (bizarrely) something like the following:
        //
        // public enum Type {
        //     /*public static final*/ ADD /* = new Type() */
        // }
        //
        // and we need to avoid transforming the "new Type()" clause or javac chokes
        boolean inEnumFieldInit = path().endsWith(".ClassDef.VarDef") &&
            Flags.isEnum(_env.enclClass.sym);

        // TODO: we can't reflectively create anonymous inner classes so maybe we should not detype
        // any constructor invocation...

        if (tree.def == null && !inEnumFieldInit) {
            // if there is a specific enclosing instance provided, use tree, otherwise use this
            // unless we're in a static context in which case use nothing
            List<JCExpression> args;
            if (tree.encl != null) {
                args = tree.args.prepend(tree.encl);
            } else if (inStatic()) {
                args = tree.args.prepend(_tmaker.at(tree.pos).Literal(TypeTags.BOT, null));
            } else {
                args = tree.args.prepend(_tmaker.at(tree.pos).Ident(_names._this));
            }
            args = args.prepend(classLiteral(tree.clazz, tree.clazz.pos));

            JCMethodInvocation invoke = callRT("newInstance", tree.pos, args);
            invoke.varargsElement = tree.varargsElement;
            result = invoke;
        }

        // if the instantiated type is a library class or interface, we need to insert runtime
        // casts to the the formal parameter types
        if (tree.def != null) {
            // isLibrary() will return false if anonParent is null (which could happen if we fail
            // to resolve its type above)
            if (!tree.args.isEmpty() && ASTUtil.isLibrary(_env.info.anonParent)) {
                List<MethodSymbol> ctors = _resolver.lookupMethods(
                    _env.info.anonParent.members(), _names.init);
                MethodSymbol best = _resolver.pickMethod(_env, ctors, tree.args);
                if (best != null) {
                    tree.args = castList(best.type.asMethodType().argtypes, tree.args);
                } else {
                    Debug.log("Unable to resolve overload", "ctors", ctors, "args", tree.args);
                }
            }
        }

        // finally restore our previous anonymous parent
        _env.info.anonParent = oanonp;
    }

    @Override public void visitNewArray (JCNewArray tree) {
        // determine the type of the array elements
        JCExpression etype = tree.elemtype;
        if (etype == null) {
            // if we have no type, we're probably inside a multidimensional array initializer, so
            // we obtain our type from our enclosing array initializer
            if (!(_env.info.arrayElemType instanceof JCArrayTypeTree)) {
                Debug.log("In nested array initializer but type is not array type?", "expr", tree,
                          "etype", _env.info.arrayElemType);
            } else {
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
            result = callRT("boxArrayArgs", tree.pos,
                            tree.elems.prepend(classLiteral(etype, etype.pos)));
        } else {
            result = callRT("boxArray", tree.pos, classLiteral(etype, etype.pos), tree);
        }
    }

    @Override public void visitSelect (JCFieldAccess tree) {
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

        // if the selected expression is a static receiver (a class) then we don't want to
        // transform, otherwise we do
        if (!isStaticReceiver(tree.selected)) {
            // transform obj.field into RT.select(obj, "field")
            result = callRT("select", tree.pos,
                            _tmaker.Literal(TypeTags.CLASS, tree.name.toString()),
                            tree.selected);
            // Debug.log("Transformed select " + tree + ": " + result);
        } else {
            // Debug.log("Not xforming select: " + tree + " (" + path + ")");
        }
    }

    @Override public void visitApply (JCMethodInvocation tree) {
//         Debug.log("Method invocation", "typeargs", tree.typeargs, "method", what(tree.meth),
//                   "args", tree.args, "varargs", tree.varargsElement);

        // resolve the called method before we transform the leaves of this tree
        Symbol msym = _resolver.resolveMethod(_env, tree);

        // we need to track whether we're processing the arguments of a this() or super()
        // constructor because that is a "static" context in that it is illegal to reference "this"
        // during that time; there's no nice way to represent that in path() so instead we track it
        // explicitly and make use of it in inStatic()... elegance--.
        boolean isChainedCons = (TreeInfo.name(tree.meth) == _names._super ||
                                 TreeInfo.name(tree.meth) == _names._this);
        boolean oldInChainedCons = _env.info.inChainedCons;
        _env.info.inChainedCons = _env.info.inChainedCons || isChainedCons;
        super.visitApply(tree);
        _env.info.inChainedCons = oldInChainedCons;

        // if this is a chained constructor call or super.foo(), we can't call it reflectively
        if (isChainedCons ||
            (tree.meth instanceof JCFieldAccess &&
             TreeInfo.name(((JCFieldAccess)tree.meth).selected) == _names._super)) {
            // but if the method is defined in a library class, we need to cast the argument types
            // back to the types it expects
            if (msym == null) {
                Debug.warn("Unable to resolve method for super()", "tree", tree);

            } else if (!tree.args.isEmpty() && ASTUtil.isLibrary(msym.owner)) {
                // we need to convert any formal type parameters on this method (as defined in the
                // super class) to the actuals provided by our class in the extends clause
                Type mtype = convertSuperMethod(msym, _env.enclClass.sym);
                tree.args = castList(mtype.asMethodType().argtypes, tree.args);
            }
            return;
        }

        // if this method has no receiver, then we're well and truly confused; leave this
        // method untransformed and let the compilation fail
        if (msym == null && !(tree.meth instanceof JCFieldAccess)) {
            Debug.log("Not transforming unresolvable method: " + tree);
            return;
        }

        String invokeName;
        JCExpression recv;
        if (msym != null && Flags.isStatic(msym)) {
            // convert to RT.invokeStatic("method", decl.class, args)
            ClassSymbol osym = (ClassSymbol)msym.owner;
            recv = classLiteral(mkFA(osym.fullname.toString(), tree.pos), tree.pos);
            invokeName = "invokeStatic";

        } else if (tree.meth instanceof JCFieldAccess) {
            // convert to RT.invoke("method", receiver, args)
            recv = ((JCFieldAccess)tree.meth).selected;
            invokeName = "invoke";

        } else {
            if (msym == null) {
                // if this method has a receiver, assume a non-static method application; we do
                // this because if we were unable to resolve a symbol for this method, that may be
                // because the receiver has "provisional" type (and we would naturally know nothing
                // about it) TODO: look up a symbol for the receiver to confirm that it's untyped
                Debug.log("Assuming non-static apply for unresolvable method: " + tree);
            }            

            // convert to RT.invoke("method", this, args)
            recv = _tmaker.at(tree.meth.pos).Ident(_names._this);
            invokeName = "invoke";
        }

        tree.args = tree.args.prepend(recv).
            prepend(_tmaker.Literal(TypeTags.CLASS, TreeInfo.name(tree.meth).toString()));
        tree.meth = mkRT(invokeName, tree.meth.pos);
        // Debug.log("APPLY " + msym + " -> " + tree);
    }

    @Override public void visitSwitch (JCSwitch tree) {
        // we need to determine the static type of the selector and cast back to that to avoid a
        // complex transformation of switch into an equivalent set of if statements nested inside a
        // one loop for loop (to preserve 'break' semantics)
        Type type = _resolver.resolveType(_env, tree.selector);

        // we have to look up the type *before* we transform the switch expression
        super.visitSwitch(tree);

        // we have to apply our checked cast *after* we transform the switch expression
        if (type == null) {
            Debug.warn("Can't resolve type for switch " + tree.selector);

        } else if (Flags.isEnum(type.tsym)) {
            // type.toString() gives us back a source representation of the type
            tree.selector = checkedCast(mkFA(type.toString(), tree.selector.pos), tree.selector);

        } else {
            // for integer types, we need to use asInt() rather than casting to Integer because
            // Java won't unbox and then coerce, whereas it will coerce; so if our switch
            // expression is int and the case constants are char, compilation will fail if we
            // promote the int to Integer
            tree.selector = callRT("asInt", tree.selector.pos, tree.selector);
        }
    }

    @Override public void visitIf (JCIf tree) {
        super.visitIf(tree);

        // we need to cast the if expression to boolean
        tree.cond = callRT("asBoolean", tree.cond.pos, tree.cond);
    }

    @Override public void visitConditional (JCConditional tree) {
        super.visitConditional(tree);

        // we need to cast the if expression to boolean
        tree.cond = callRT("asBoolean", tree.cond.pos, tree.cond);
    }

    @Override public void visitDoLoop (JCDoWhileLoop tree) {
        super.visitDoLoop(tree);

        // we need to cast the while expression to boolean
        tree.cond = callRT("asBoolean", tree.cond.pos, tree.cond);
    }

    @Override public void visitWhileLoop (JCWhileLoop tree) {
        super.visitWhileLoop(tree);

        // we need to cast the while expression to boolean
        tree.cond = callRT("asBoolean", tree.cond.pos, tree.cond);
    }

    @Override public void visitForLoop (JCForLoop tree) {
        super.visitForLoop(tree);

        // "for (;;)" will have null condition
        if (tree.cond != null) {
            // we need to cast the for condition expression to boolean
            tree.cond = callRT("asBoolean", tree.cond.pos, tree.cond);
        }
    }

    @Override public void visitForeachLoop (JCEnhancedForLoop tree) {
        super.visitForeachLoop(tree);

        // rewrite the foreach loop as: foreach (iter : RT.asIterable(expr))
        tree.expr = callRT("asIterable", tree.expr.pos, tree.expr);
    }

    @Override public void visitTry (JCTry tree) {
        super.visitTry(tree);

        // insert an inner try/catch that catches RuntimeException, dynamically checks whether its
        // cause is the caught type and casts and rethrows the cause if so
        if (!tree.catchers.isEmpty()) {
            Name cvname = _names.fromString("_rt_" + tree.catchers.head.param.name);
            JCCatch catcher = _tmaker.Catch(
                _tmaker.VarDef(_tmaker.Modifiers(0L), cvname, mkFA("RuntimeException", 0), null),
                _tmaker.Block(0L, List.of(unwrapExns(cvname, tree.catchers))));
            tree.body = _tmaker.Block(
                0L, List.<JCStatement>of(_tmaker.Try(tree.body, List.of(catcher), null)));
        }
    }

    @Override public void visitTypeCast (JCTypeCast tree) {
        super.visitTypeCast(tree);
        Type ctype = _resolver.resolveAsType(_env, tree.clazz, false);
        if (ctype != null) {
            if (!(tree.clazz instanceof JCExpression)) {
                Debug.warn("Got cast to non-JCExpression node?", "tree", tree);
            } else {
                result = callRT("noteCast", tree.pos,
                                classLiteral((JCExpression)tree.clazz, tree.pos), tree.expr);
            }
        }
    }

    @Override public void visitIndexed (JCArrayAccess tree) {
        super.visitIndexed(tree);

        // rewrite the array dereference as: RT.atIndex(array, index)
        result = callRT("atIndex", tree.pos, tree.indexed, tree.index);
    }

    @Override public void visitAssign (JCAssign tree) {
        // we don't call super as we may need to avoid translating the LHS
        result = mkAssign(tree.lhs, translate(tree.rhs), tree.pos);
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
        _rootmaker = TreeMaker.instance(ctx);

        // a class that will enclose all outer classes
        _predefClassDef = _rootmaker.ClassDef(
            _rootmaker.Modifiers(Flags.PUBLIC),
            _syms.predefClass.name, null, null, null, null);
        _predefClassDef.sym = _syms.predefClass;
    }

    protected boolean inLibraryOverrider ()
    {
        // if we're passed a method from an anonymous inner class, it will have no symbol
        // information and we currently instead fall back on a hack that marks all methods in the
        // inner class as detypable or not based on whether the parent of the anonymous inner class
        // is a library class or not; this is not strictly correct, but strict correctness is going
        // to be a huge pile of trouble that we want to make sure is worth it first
        if (_env.info.anonParent != null) {
            return ASTUtil.isLibrary(_env.info.anonParent);
        }

        // if we're not in a method, we can't be in a library overrider
        if (_env.enclMethod == null) {
            return false;
        }

        // TODO: make this more correct: only match public static methods named 'main' with a
        // single String[] argument
        return _env.enclMethod.getName().toString().equals("main") ||
            ASTUtil.isLibraryOverrider(_types, _env.enclMethod.sym);
    }

    protected boolean isStaticReceiver (JCExpression fa)
    {
        // Debug.log("isStaticReceiver(" + fa + ")");

        // if we've already transformed this receiver, it will be a method invocation
        if (!(fa instanceof JCIdent || fa instanceof JCFieldAccess)) {
            return false;
        }

        // if it's some variable in scope (cheap test), it's not a static receiver
        if (isScopedVar(fa)) {
            return false;
        }

        // otherwise try resolving this expression as a type
        return (_resolver.resolveAsType(_env, fa, false) != null);
    }

    protected boolean isScopedVar (JCExpression expr)
    {
        if (expr instanceof JCFieldAccess) {
            return isScopedVar(((JCFieldAccess)expr).selected);

        } else if (expr instanceof JCIdent) {
            Name name = ((JCIdent)expr).name;
            // 'this' is always a reference (for our purposes)
            return name == _names._this || (_resolver.findVar(_env, name) != null);

        } else {
            Debug.log("isScopedVar on weird expr: " + expr);
            return false;
        }
    }

    protected boolean inStatic () {
        return _env.info.inChainedCons || (_env.enclMethod == null) ||
            (_env.enclMethod.mods != null && (_env.enclMethod.mods.flags & Flags.STATIC) != 0);
    }

    protected JCMethodInvocation unop (int pos, Tree.Kind op, JCExpression arg)
    {
        JCLiteral opcode = _tmaker.at(pos).Literal(TypeTags.CLASS, op.toString());
        return callRT("unop", opcode.pos, opcode, arg);
    }

    protected JCMethodInvocation binop (int pos, Tree.Kind op, JCExpression lhs, JCExpression rhs)
    {
        JCLiteral opcode = _tmaker.at(pos).Literal(TypeTags.CLASS, op.toString());
        return callRT("binop", opcode.pos, opcode, lhs, rhs);
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
        inv.typeargs = List.<JCExpression>of(_tmaker.Ident(ptype.tsym.name));
        return inv;
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
        if (lhs instanceof JCArrayAccess) {
            JCArrayAccess aa = (JCArrayAccess)lhs;
            return callRT("assignAt", pos, translate(aa.indexed), translate(aa.index), rhs);

        } else if (lhs instanceof JCFieldAccess) {
            JCFieldAccess fa = (JCFieldAccess)lhs;
            // if the expression is "this.something = ...", we want to avoid turning the lhs into a
            // reflective assignment; we want to preserve definite assignment in constructors
            if (fa.selected instanceof JCIdent && ((JCIdent)fa.selected).name == _names._this) {
                // TODO: resolve the field on the lhs and only preserve definite assignment if it
                // exists and it's a final field
                return _tmaker.at(pos).Assign(lhs, rhs);
            } else {
                return callRT("assign", pos, translate(fa.selected),
                              _tmaker.Literal(TypeTags.CLASS, fa.name.toString()), rhs);
            }

        // TODO: we need to handle (foo[ii]) = 1 (and maybe others?)
        } else {
            return _tmaker.at(pos).Assign(translate(lhs), rhs);
        }
    }

    protected JCExpression classLiteral (JCExpression expr, int pos)
    {
        // TODO: validate that we got passed either Name or package.Name
        if ("int".equals(expr.toString())) {
            expr = _tmaker.Ident(_names.fromString("Integer"));
        }
        return _tmaker.at(pos).Select(expr, _names._class);
    }

    protected JCStatement unwrapExns (Name cvname, List<JCCatch> catchers)
    {
        if (catchers.isEmpty()) {
            return _tmaker.Throw(_tmaker.Ident(cvname));
        }
        Name ctname = TreeInfo.name(catchers.head.param.vartype);
        Name gcname = _names.fromString("getCause");
        return _tmaker.If(
            _tmaker.TypeTest(
                _tmaker.Apply(List.<JCExpression>nil(),
                              _tmaker.Select(_tmaker.Ident(cvname), gcname),
                              List.<JCExpression>nil()),
                _tmaker.Ident(ctname)),
            _tmaker.Throw(
                _tmaker.TypeCast(
                    _tmaker.Ident(ctname),
                    _tmaker.Apply(List.<JCExpression>nil(),
                                  _tmaker.Select(_tmaker.Ident(cvname), gcname),
                                  List.<JCExpression>nil()))),
            unwrapExns(cvname, catchers.tail));
    }

    protected Type convertSuperMethod (Symbol msym, ClassSymbol sym)
    {
        // if the class is not a class, just return the method type as originally defined
        if (sym.type.tag != TypeTags.CLASS) {
            return msym.type;
        } else {
            // otherwise obtain the type of sym's super class with formal type parameters bound to
            // the actuals provided when sym extended it
            Type stype = ((Type.ClassType)sym.type).supertype_field;
            // then substitute those actuals for formals in the method's type
            return _types.subst(msym.type, msym.owner.type.allparams(), stype.allparams());
        }
    }

    protected List<JCExpression> castIntList (List<JCExpression> list)
    {
        if (list.isEmpty()) {
            return list;
        } else {
            return castIntList(list.tail).prepend(
                checkedCast(_tmaker.Ident(_names.fromString("Integer")), list.head));
        }
    }

    protected List<JCExpression> castList (List<Type> params, List<JCExpression> list)
    {
        if (list.isEmpty()) {
            return list;
        }

        // if the type in question is a type variable, find its upper bound
        Type ptype = params.head;
        boolean needTypeVarCast = false;
        while (ptype.tag == TypeTags.TYPEVAR) {
            ptype = ptype.getUpperBound();
            needTypeVarCast = true;
        }

        JCExpression clazz = mkFA(ptype.toString(), list.head.pos);
        JCMethodInvocation cexpr = needTypeVarCast ?
            typeVarCast(clazz, list.head, params.head) : checkedCast(clazz, list.head);

        return castList(params.tail, list.tail).prepend(cexpr);
    }

    protected List<JCVariableDecl> toTypeArgs (List<JCVariableDecl> params)
    {
        if (params.isEmpty()) {
            return List.nil();
        }
        return toTypeArgs(params.tail).prepend(
            _tmaker.VarDef(_tmaker.Modifiers(params.head.mods.flags | Flags.FINAL),
                           params.head.name.append(_names.fromString(TP_SUFFIX)),
                           params.head.vartype, null));
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

    protected Resolver _resolver;
    protected Types _types;
    // protected Name.Table _names;
    protected Names _names;
    protected Enter _enter;
    protected MemberEnter _memberEnter;
    protected Attr _attr;
    protected Symtab _syms;
    protected Annotate _annotate;
    protected TreeMaker _rootmaker;

    protected static final String TP_SUFFIX = "$T";
    protected static final Context.Key<Detype> DETYPE_KEY = new Context.Key<Detype>();
}
