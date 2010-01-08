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

        // note the environment of the class we're processing
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(tree.sym.members_field.dupUnshared()));
        _env.enclClass = tree;
        _env.outer = oenv;

        if (tree.name != _names.empty) {
            // add our @Transformed annotation to the AST
            JCAnnotation a = setPos(_tmaker.Annotation(mkFA(Transformed.class.getName(), tree.pos),
                                                       List.<JCExpression>nil()), tree.pos);
            tree.mods.annotations = tree.mods.annotations.prepend(a);

            // since the annotations AST has already been resolved into type symbols, we have to
            // manually add a type symbol for annotation to the ClassSymbol
            tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                Backdoor.enterAnnotation(_annotate, a, _syms.annotationType,
                                         _enter.getEnv(tree.sym)));
        }

        super.visitClassDef(tree);
        _env = oenv;

        Debug.log("Leaving class " + tree.name);
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
        Debug.log("Visiting method def", "name", tree.name);

        // create a local environment for this method definition
        Env<DetypeContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(oenv.info.scope.dupUnshared()));
        _env.enclMethod = tree;
        _env.info.scope.owner = tree.sym;

        // now we can call super and translate our children
        super.visitMethodDef(tree);

        // transform the return type if we're not in a library overrider and it is not void
        if (tree.restype != null && !ASTUtil.isVoid(tree.restype) && !inLibraryOverrider()) {
            tree.restype = _tmaker.Ident(_names.fromString("Object"));
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
            Type vtype = _resolver.resolveAsType(_env, tree.vartype);
            _env.info.scope.enter(new VarSymbol(0, tree.name, vtype, _env.info.scope.owner));
        }

        // overriding method
        // we don't want to detype the param(s) of a catch block
        String path = path();
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
        super.visitUnary(tree);

        JCExpression expr;
        JCLiteral one = _tmaker.Literal(TypeTags.INT, 1);
        switch (tree.getKind()) {
        case PREFIX_INCREMENT:  // ++i -> (i = i + 1)
        case POSTFIX_INCREMENT: // i++ -> ((i = i + 1) - 1)
            expr = _tmaker.Assign(tree.arg, binop(tree.pos, Tree.Kind.PLUS, tree.arg, one));
            if (tree.getKind() == Tree.Kind.POSTFIX_INCREMENT) {
                expr = binop(tree.pos, Tree.Kind.MINUS, expr, one);
            }
            break;

        case PREFIX_DECREMENT:  // --i -> (i = i - 1)
        case POSTFIX_DECREMENT: // i-- -> ((i = i - 1) + 1)
            expr = _tmaker.Assign(tree.arg, binop(tree.pos, Tree.Kind.MINUS, tree.arg,
                                                  _tmaker.Literal(TypeTags.INT, 1)));
            if (tree.getKind() == Tree.Kind.POSTFIX_DECREMENT) {
                expr = binop(tree.pos, Tree.Kind.PLUS, expr, one);
            }
            break;

        default:
            expr = unop(tree.pos, tree.getKind(), tree.arg);
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
        super.visitAssignop(tree);

        // TODO: this a problem wrt evaluating the LHS more than once, we probably need to do
        // something painfully complicated
        JCExpression bop = _tmaker.Binary(tree.getTag() - JCTree.ASGOffset, tree.lhs, tree.rhs);
        bop = binop(tree.pos, bop.getKind(), tree.lhs, tree.rhs);
        result = _tmaker.at(tree.pos).Assign(tree.lhs, bop);
        // Debug.log("Rewrote assignop", "kind", tree.getKind(), "into", result);
    }

    @Override public void visitNewClass (JCNewClass tree) {
        Debug.log("Class instantiation", "typeargs", tree.typeargs, "class", what(tree.clazz),
                 "args", tree.args);

        // if we see an anonymous inner class declaration, resolve the type of the to-be-created
        // class, we need this in inLibraryOverrider() for our approximation approach
        Symbol oanonp = _env.info.anonParent;
        if (tree.def != null) {
            Name cname = (tree.clazz instanceof JCTypeApply) ?
                TreeInfo.fullName(((JCTypeApply)tree.clazz).clazz) : TreeInfo.fullName(tree.clazz);
            _env.info.anonParent = _resolver.findType(_env, cname);
            if (_env.info.anonParent == null) {
                Debug.log("Pants! Unable to resolve type of anonymous inner parent", "name", cname);
            }

            // we need to create a fake class symbol for our anonymous inner class and do our own
            // partial Enter on it to obtain enough symbol information to keep everything working
            Symbol encsym = (_env.enclMethod == null) ? _env.enclClass.sym : _env.enclMethod.sym;
            ClassSymbol csym = new ClassSymbol(0, tree.def.name, encsym);
            csym.members_field = new Scope(csym); // TODO: do we want a next?
            if (tree.def.sym != null) {
                Debug.log("Eh? Anon-inner-class already has a symbol!?");
            } else {
                tree.def.sym = csym;
            }

// TODO: enter methods, etc.
//             tree.def.accept(new TreeScanner() {
//                 @Override public void visitMethodDef (JCMethodDecl tree) {
//                 }
//             });
        }
        super.visitNewClass(tree);

// TODO: we can't reflectively create anonymous inner classes so maybe we should not detype
// constructor invocation, but rather directly inject the extra type tag arguments...

        if (tree.def == null) {
            // if there is a specific enclosing instance provided, use tree, otherwise use this
            // unless we're in a static context in which case use nothing
            List<JCExpression> args;
            if (tree.encl != null) {
                args = tree.args.prepend(tree.encl);
            } else if (inStatic()) {
                args = tree.args.prepend(_tmaker.Literal(TypeTags.BOT, null));
            } else {
                args = tree.args.prepend(_tmaker.Ident(_names._this));
            }
            args = args.prepend(classLiteral(tree.clazz, tree.clazz.pos));

            JCMethodInvocation invoke = callRT("newInstance", tree.pos, args);
            invoke.varargsElement = tree.varargsElement;
            result = invoke;

        // if the instantiated type is a library class or interface, we need to insert runtime
        // casts to the the formal parameter types
        } else {
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

            // clear out the fake symbol we created for our anonymous inner class
            tree.def.sym = null;
        }

        // finally restore our previous anonymous parent
        _env.info.anonParent = oanonp;
    }

    @Override public void visitNewArray (JCNewArray tree) {
        super.visitNewArray(tree);

        Debug.log("Array creation", "expr", tree, "dims", tree.dims, "elems", tree.elems);

        if (tree.elemtype == null) {
            Debug.log("Hrm? " + tree);
            return;
        }

        // we need to cast the dimension expressions to int
        tree.dims = castIntList(tree.dims);

        JCExpression otype = tree.elemtype;
// TODO: something funny happens here
//             tree.elemtype = setPos(_tmaker.Ident(_names.java_lang_Object), otype.pos);
        result = callRT("boxArray", tree.pos, classLiteral(otype, otype.pos), tree);

//             // either we have dimensions or a set of initial values, but not both
//             if (tree.elems != null) {
//                 // TODO
//             } else if (!tree.dims.isEmpty()) {
//                 result = callRT("newArray", tree.pos,
//                                 tree.dims.prepend(classLiteral(tree.elemtype, tree.elemtype.pos)).
//                                 toArray(new JCExpression[tree.dims.size()]));
//             }
    }

// TODO: this is fiddlier
//         @Override public void visitThrow (JCThrow tree) {
//             super.visitThrow(tree);

//             // add a cast to Throwable on the expression being thrown since we will have detyped it
//             tree.expr = _tmaker.TypeCast(_tmaker.Ident(_names.fromString("Throwable")), tree.expr);
//         }

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
        wantXform = wantXform && !path.contains(".Apply.meth");
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
            // Debug.log("Transformed select " + tree + " (" + path + ")");
        } else {
            Debug.log("Not xforming select: " + tree + " (" + path + ")");
        }
    }

    @Override public void visitApply (JCMethodInvocation tree) {
//         Debug.log("Method invocation", "typeargs", tree.typeargs, "method", what(tree.meth),
//                   "args", tree.args, "varargs", tree.varargsElement);

// TODO: if we get this working properly, everything below becomes vastly simplified
//         MethodSymbol msym = _resolver.resolveMethod(_env, tree);
//         Debug.log("Method invoke " + tree + " -> " + msym);

        super.visitApply(tree);

        // transform expr.method(args)
        if (tree.meth instanceof JCFieldAccess) {
            JCFieldAccess mfacc = (JCFieldAccess)tree.meth;

            if (isStaticReceiver(mfacc.selected)) {
                // convert to RT.invokeStatic("method", mfacc.selected.class, args)
                tree.args = tree.args.
                    prepend(classLiteral(mfacc.selected, mfacc.selected.pos)).
                    prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                tree.meth = mkRT("invokeStatic", mfacc.pos);
                return;
            }

            // TODO: if receiver is statically imported field we need to prepend classname

            // convert to RT.invoke("method", expr, args)
            tree.args = tree.args.prepend(mfacc.selected).
                prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
            tree.meth = mkRT("invoke", mfacc.pos);

        // transform method(args)
        } else if (tree.meth instanceof JCIdent) {
            JCIdent mfid = (JCIdent)tree.meth;
            if (mfid.name == _names._super) {
                return; // super() cannot be transformed so we leave it alone
            }

            // resolve the method in question (TODO: this should at least take the argument count
            // if not whatever type information we have)
            Symbol nsym = _resolver.findMethod(_env, mfid.name);
            if (nsym == null) {
                Debug.log("!!! Not transforming unresolvable method: " + tree);
                return;
            }

            // determine whether this method is static or non-static
            if (Flags.isStatic(nsym)) {
                // convert to RT.invokeStatic("method", decl.class, args)
                ClassSymbol osym = (ClassSymbol)nsym.owner;
                tree.args = tree.args. // TODO: better pos than tree.pos
                    prepend(classLiteral(mkFA(osym.fullname.toString(), tree.pos), tree.pos)).
                    prepend(_tmaker.Literal(TypeTags.CLASS, mfid.toString()));
                tree.meth = mkRT("invokeStatic", mfid.pos);
                return;
            }

            // the method is not static so we get RT.invoke("method", this, args)
            JCIdent recid = _tmaker.Ident(_names._this);
            recid.pos = mfid.pos;
            tree.args = tree.args.prepend(recid).
                prepend(_tmaker.Literal(TypeTags.CLASS, mfid.toString()));
            tree.meth = mkRT("invoke", mfid.pos);
//                     Debug.log("Mutated", "typeargs", tree.typeargs, "method", what(tree.meth),
//                              "args", tree.args, "varargs", tree.varargsElement);

        // are there other types of invocations?
        } else {
            Debug.log("Unknown invocation?", "typeargs", tree.typeargs,
                     "method", what(tree.meth), "args", tree.args,
                     "varargs", tree.varargsElement);
        }
    }

    @Override public void visitSwitch (JCSwitch tree) {
        // we need to determine the static type of the selector and cast back to that to avoid a
        // complex transformation of switch into an equivalent set of if statements nested inside a
        // one loop for loop (to preserve break semantics)
        Type type = _resolver.resolveType(_env, tree.selector);
        if (type == null) {
            Debug.log("!!! Can't resolve type for switch " + tree.selector);
        }

        // we have to look up the type *before* we transform the switch expression
        super.visitSwitch(tree);

        // we have to apply our checked cast *after* we transform the switch expression
        if (type != null) {
            // type.toString() gives us back a source representation of the type
            tree.selector = checkedCast(mkFA(type.toString(), tree.selector.pos), tree.selector);
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

    @Override public void visitIndexed (JCArrayAccess tree) {
        super.visitIndexed(tree);

        // rewrite the array dereference as: RT.atIndex(array, index)
        result = callRT("atIndex", tree.pos, tree.indexed, tree.index);
    }

    @Override public void visitAssign (JCAssign tree) {
        if (tree.lhs instanceof JCArrayAccess) {
            JCArrayAccess aa = (JCArrayAccess)tree.lhs;
            result = callRT("assignAt", tree.pos, translate(aa.indexed), translate(aa.index),
                            translate(tree.rhs));
        } else if (tree.lhs instanceof JCFieldAccess) {
            JCFieldAccess fa = (JCFieldAccess)tree.lhs;
            result = callRT("assign", tree.pos, translate(fa.selected),
                            _tmaker.Literal(TypeTags.CLASS, fa.name.toString()),
                            translate(tree.rhs));
        } else {
            super.visitAssign(tree);
        }
        // TODO: we need to handle (foo[ii]) = 1 (and maybe others?)
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
        return (_resolver.resolveAsType(_env, fa) != null);
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
        return (_env.enclMethod == null) ||
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

    protected JCExpression checkedCast (JCExpression clazz, JCExpression expr) {
        return callRT("checkedCast", expr.pos, classLiteral(clazz, expr.pos), expr);
    }

    protected JCMethodInvocation callRT (String method, int pos, JCExpression... args) {
        return setPos(_tmaker.Apply(null, mkRT(method, pos), List.from(args)), pos);
    }

    protected JCMethodInvocation callRT (String method, int pos, List<JCExpression> args) {
        return setPos(_tmaker.Apply(null, mkRT(method, pos), args), pos);
    }

    protected JCExpression mkRT (String method, int pos) {
        return mkFA(RT.class.getName() + "." + method, pos);
    }

    protected JCExpression mkFA (String fqName, int pos) {
        JCExpression expr;
        int didx = fqName.lastIndexOf(".");
        if (didx == -1) {
            expr = _tmaker.Ident(_names.fromString(fqName)); // simple identifier
        } else {
            expr = _tmaker.Select(mkFA(fqName.substring(0, didx), pos), // nested FA expr
                                  _names.fromString(fqName.substring(didx+1)));
        }
        return setPos(expr, pos);
    }

    protected JCExpression classLiteral (JCExpression expr, int pos) {
        // TODO: validate that we got passed either Name or package.Name
        if ("int".equals(expr.toString())) {
            expr = _tmaker.Ident(_names.fromString("Integer"));
        }
        return setPos(_tmaker.Select(expr, _names._class), pos);
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
        } else {
            JCExpression clazz = mkFA(params.head.toString(), list.head.pos);
            return castList(params.tail, list.tail).prepend(checkedCast(clazz, list.head));
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

    protected static <T extends JCTree> T setPos (T tree, int pos)
    {
        tree.pos = pos;
        return tree;
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

    protected static final Context.Key<Detype> DETYPE_KEY = new Context.Key<Detype>();
}
