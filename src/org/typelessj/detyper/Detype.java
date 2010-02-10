//
// $Id$

package org.typelessj.detyper;

import java.util.Set;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
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

        // copy over detype context parts that are useful to attr context
        Backdoor.setScope(aenv.info, env.info.scope);

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

    @Override public void visitClassDef (JCClassDecl tree)
    {
        Debug.log("Visiting class '" + tree.name + "'", "sym", tree.sym);

        // don't detype annotation classes; that causes myriad problems
        if ((tree.mods.flags & Flags.ANNOTATION) != 0) {
            result = tree;
            return;
        }

        // local classes have not been entered yet, so we will manually cause entering to happen
        // now; javac will overwrite our bogus symbol data later during the attrib phase
        // if ((_env.info.scope.owner.kind & (Kinds.VAR | Kinds.MTH)) != 0) {
        if (tree.sym == null) {
            Backdoor.classEnter.invoke(_enter, tree, toAttrEnv(_env));
            Debug.log("Entered inner class '" + tree.name + "'", "sym", tree.sym);
        }

        Env<AttrContext> eenv = _enter.getEnv(tree.sym);
        // Debug.log("Class attr context " + Backdoor.getScope(eenv.info));

        // note the environment of the class we're processing
        Env<DetypeContext> oenv = _env;
        // _env = _env.dup(tree, oenv.info.dup(tree.sym.members_field.dupUnshared()));
        _env = _env.dup(tree, oenv.info.dup(Backdoor.getScope(eenv.info)));
        _env.enclClass = tree;
        _env.outer = oenv;

        if (tree.name != _names.empty) {
            // add our @Transformed annotation to the AST
            JCAnnotation a = _tmaker.at(tree.pos).Annotation(
                mkFA(Transformed.class.getName(), tree.pos), List.<JCExpression>nil());
            tree.mods.annotations = tree.mods.annotations.prepend(a);

            // since the annotations AST has already been resolved into type symbols, we have
            // to manually add a type symbol for annotation to the ClassSymbol
            tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                Backdoor.enterAnnotation.invoke(
                    _annotate, a, _syms.annotationType, _enter.getEnv(tree.sym)));
        }

        super.visitClassDef(tree);
        _env = oenv;

        Debug.log("Leaving class " + tree.name);
    }

    @Override public void visitMethodDef (JCMethodDecl tree)
    {
        Debug.log("Visiting method def", "name", tree.name);

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
        List<JCVariableDecl> sigargs = isLib ? List.<JCVariableDecl>nil() : toTypeArgs(tree.params);

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

    @Override public void visitVarDef (JCVariableDecl tree)
    {
        // if we're declaring an array, put its type in the arrayElemType as we may encounter a
        // bare array initializer expression (i.e. int[] foo = { 1, 2, 3}) and we'll need to use
        // this declared type when detyping the initializer
        JCExpression oetype = _env.info.arrayElemType;
        if (tree.vartype instanceof JCArrayTypeTree) {
            _env.info.arrayElemType = tree.vartype;
        }
        super.visitVarDef(tree);
        _env.info.arrayElemType = oetype;

        // var symbols for member-level variables are already entered, we just want to handle
        // formal parameters and local variable declarations
        if (_env.tree.getTag() != JCTree.CLASSDEF) {
            // create a placeholder VarSymbol for this variable so that we can use it later
            // during some simple name resolution
            Type vtype = _resolver.resolveType(_env, tree.vartype, Kinds.TYP);
            _env.info.scope.enter(new VarSymbol(0, tree.name, vtype, _env.info.scope.owner));
        }

        String path = path();
        if (isInlinableField(tree) || (tree.sym != null && Flags.isEnum(tree.sym))) {
            // we don't want to detype a field decl that could be inlined (it could break static
            // analysis that the program may rely on for correct compilation); we also avoid
            // touching synthesized enum field declarations in any way as that angers javac greatly

        } else if (!path.contains(".Catch") && // don't detype the param(s) of a catch block
                   // nor the arguments of a library
                   !(path.contains(".MethodDef.params") && inLibraryOverrider())) {
//             Debug.log("Transforming vardef", "mods", tree.mods, "name", tree.name,
//                      "vtype", what(tree.vartype), "init", tree.init,
//                      "sym", ASTUtil.expand(tree.sym));
            tree.vartype = _tmaker.Ident(_names.fromString("Object"));
            if ((tree.mods.flags & Flags.VARARGS) != 0) {
                tree.vartype = _tmaker.TypeArray(tree.vartype);
            }
        }
    }

    @Override public void visitReturn (JCReturn tree)
    {
        super.visitReturn(tree);

        // if we're in a method whose signature cannot be transformed, we must cast the result of
        // the return type back to the static method return type
        if (tree.expr != null && inLibraryOverrider()) {
            tree.expr = checkedCast(_env.enclMethod.restype, tree.expr);
            // tree.expr = cast(_env.enclMethod.sym.type.asMethodType().restype, tree.expr);
        }
    }

    @Override public void visitUnary (JCUnary tree)
    {
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

    @Override public void visitBinary (JCBinary tree)
    {
        super.visitBinary(tree);

        JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
        result = callRT("binop", tree.pos, opcode, tree.lhs, tree.rhs);
        // Debug.log("Rewrote binop", "kind", tree.getKind(), "tp", tree.pos, "ap", opcode.pos);
    }

    @Override public void visitAssignop (JCAssignOp tree)
    {
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

    @Override public void visitNewClass (JCNewClass tree)
    {
//         Debug.log("Class instantiation", "typeargs", tree.typeargs, "class", what(tree.clazz),
//                   "args", tree.args);

        // we need a new environment here to keep our Env tree isomorphic to javac's
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup());

        // if we see an anonymous inner class declaration, resolve the type of the to-be-created
        // class, we need this in inLibraryOverrider() for our approximation approach
        if (tree.def != null) {
            Type atype = _resolver.resolveType(_env, tree.clazz, Kinds.TYP);
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

            // TODO: we can't reflectively create anonymous inner classes so maybe we should not
            // detype any constructor invocation...
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
                // TODO: switch this to resolve method
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

        // if the selected expression is a static receiver (a class) then we don't want to
        // transform, otherwise we do
        if (!_resolver.isStaticSite(_env, tree.selected)) {
            // transform obj.field into RT.select(obj, "field")
            result = callRT("select", tree.pos,
                            _tmaker.Literal(TypeTags.CLASS, tree.name.toString()),
                            tree.selected);
        }
    }

    @Override public void visitApply (JCMethodInvocation tree)
    {
//         Debug.log("Method invocation", "typeargs", tree.typeargs, "method", what(tree.meth),
//                   "args", tree.args, "varargs", tree.varargsElement);

        // if this is a zero args super() call, we'll be doing no detyping and if we try to resolve
        // the method we may run into annoying warnings relating to the fact that enums have no
        // legal super constructor
        Name mname = TreeInfo.name(tree.meth);
        if (mname == _names._super && tree.args.isEmpty()) {
            result = tree;
            return;
        }

        // resolve the called method before we transform the leaves of this tree
        Symbol msym = _resolver.resolveMethod(_env, tree).msym;
        if (msym.kind >= Kinds.ERR) {
            result = tree; // abort! (error will have been logged)
            return;
        }
        Debug.log("Method invocation", "tree", tree, "sym", msym);

        // we need to track whether we're processing the arguments of a this() or super()
        // constructor because that is a "static" context in that it is illegal to reference "this"
        // during that time; there's no nice way to represent that in path() so instead we track it
        // explicitly and make use of it in inStatic()... elegance--.
        boolean isChainedCons = (mname == _names._super || mname == _names._this);
        boolean oldInChainedCons = _env.info.inChainedCons;
        _env.info.inChainedCons = _env.info.inChainedCons || isChainedCons;
        super.visitApply(tree);
        _env.info.inChainedCons = oldInChainedCons;

        // if this is a chained constructor call or super.foo(), we can't call it reflectively
        if (isChainedCons || isSuperMethodCall(tree)) {
            if (!tree.args.isEmpty()) {
                List<Type> atypes = _types.memberType(_env.enclClass.sym.type, msym).
                    asMethodType().argtypes;
                // if the method is defined in a library class, we need to cast the argument types
                // back to the types it expects
                if (ASTUtil.isLibrary(msym.owner)) {
                    // we need to convert any formal type parameters on this method (as defined in
                    // the super class) to the actuals provided by our class in the extends clause
                    tree.args = castList(atypes, tree.args);
                } else {
                    // if the declarer is not a library class, we need to insert type carrying
                    // arguments that match the types of the method we resolved; if the resolved
                    // method is overloaded, this will disambiguate, and even if it's not
                    // overloaded, we need something legal in those argument positions
                    tree.args = tree.args.appendList(toTypedNulls(atypes, tree.args));
                }
            }
            return;
        }

        String invokeName;
        JCExpression recv;
        if (Flags.isStatic(msym)) {
            // convert to RT.invokeStatic("method", decl.class, args)
            ClassSymbol osym = (ClassSymbol)msym.owner;
            recv = classLiteral(mkFA(osym.fullname.toString(), tree.pos), tree.pos);
            invokeName = "invokeStatic";

        } else if (tree.meth instanceof JCFieldAccess) {
            // convert to RT.invoke("method", receiver, args)
            recv = ((JCFieldAccess)tree.meth).selected;
            invokeName = "invoke";

        } else {
            // convert to RT.invoke("method", this, args)
            recv = _tmaker.at(tree.meth.pos).Ident(_names._this);
            invokeName = "invoke";
        }

        tree.args = tree.args.prepend(recv).
            prepend(_tmaker.Literal(TypeTags.CLASS, TreeInfo.name(tree.meth).toString()));
        tree.meth = mkRT(invokeName, tree.meth.pos);
        // Debug.log("APPLY " + msym + " -> " + tree);
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
            // type.toString() gives us back a source representation of the type
            tree.selector = checkedCast(mkFA(type.toString(), tree.selector.pos), tree.selector);
            // tree.selector = cast(type, tree.selector);

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

    @Override public void visitTypeCast (JCTypeCast tree)
    {
        super.visitTypeCast(tree);

        if (!(tree.clazz instanceof JCExpression)) {
            Debug.warn("Got cast to non-JCExpression node?", "tree", tree);
        } else if (!path().endsWith("Switch.Case")) {
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

    protected boolean inStatic ()
    {
        return _env.info.inChainedCons || (_env.enclMethod == null) ||
            (_env.enclMethod.mods != null && (_env.enclMethod.mods.flags & Flags.STATIC) != 0);
    }

    protected boolean isInlinableField (JCVariableDecl tree)
    {
        // TODO: detect expressions that could be folded into constants
        return ASTUtil.isStaticFinal(tree.mods) && (tree.init.getTag() == JCTree.LITERAL);
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

    /**
     * Wraps a conditional expression in a call to {@link RT#asBoolean} iff the expression is not a
     * literal that might be influencing static analysis of the underyling conditional.
     */
    protected JCExpression condCast (JCExpression expr)
    {
        switch (expr.getTag()) {
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
        JCExpression clazz = mkFA(etype.toString(), expr.pos);
        return (etype != type) ? typeVarCast(clazz, expr, type) : checkedCast(clazz, expr);
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

    protected JCExpression typeToTree (Type type, int pos)
    {
        switch (type.tag) {
        case TypeTags.CLASS: {
            // TODO: enclosing class?
            JCExpression clazz = mkFA(type.tsym.getQualifiedName().toString(), pos);
            return type.getTypeArguments().isEmpty() ? clazz :
                _tmaker.at(pos).TypeApply(clazz, typesToTree(type.getTypeArguments(), pos));
        }

        case TypeTags.TYPEVAR:
            return _tmaker.at(pos).Ident(type.tsym.name);
            // TODO: do we need to worry about type.bound or type.lower?

        case TypeTags.WILDCARD: {
            Type.WildcardType wtype = (Type.WildcardType)type;
            return _tmaker.at(pos).Wildcard(
                _tmaker.at(pos).TypeBoundKind(wtype.kind), typeToTree(wtype.type, pos));
            // TODO: if wtype.bound is not null we may need to wrap this all in a TypeParameter()?
        }

        default:
            Debug.warn("Unsupported type in typeToTree", "type", type, "tag", type.tag);
            return null;
        }
    }

    protected List<JCExpression> typesToTree (List<Type> types, int pos)
    {
        return types.isEmpty() ? List.<JCExpression>nil() :
            typesToTree(types.tail, pos).prepend(typeToTree(types.head, pos));
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
                _tmaker.TypeCast( // TODO: don't need the typecast if type is Throwable
                    _tmaker.Ident(ctname),
                    _tmaker.Apply(List.<JCExpression>nil(),
                                  _tmaker.Select(_tmaker.Ident(cvname), gcname),
                                  List.<JCExpression>nil()))),
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
        return toTypeArgs(params.tail).prepend(
            _tmaker.VarDef(_tmaker.Modifiers(params.head.mods.flags | Flags.FINAL),
                           params.head.name.append(_names.fromString(TP_SUFFIX)),
                           params.head.vartype, null));
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
        int tpos = arg.pos;
        JCExpression expr;
        switch (type.tag) {
        case TypeTags.BYTE:
        case TypeTags.CHAR:
        case TypeTags.SHORT:
        case TypeTags.INT:
        case TypeTags.LONG:
        case TypeTags.FLOAT:
        case TypeTags.DOUBLE:
            return _tmaker.Literal(type.tag, 0); // TODO
        case TypeTags.BOOLEAN:
            return _tmaker.Literal(type.tag, false);
        default:
            return _tmaker.at(tpos).TypeCast(mkFA(type.toString(), tpos),
                                             _tmaker.Literal(TypeTags.BOT, null));
        }
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
