//
// $Id$

package org.typelessj.detyper;

import java.util.Set;

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
            // _env.enclClass = predefClassDef;
            _env.info.scope = tree.namedImportScope;
            tree.accept(this);
        } finally {
            _env = oenv;
        }
    }

    @Override public void visitTopLevel (JCCompilationUnit tree) {
        _tmaker = _rootmaker.forToplevel(tree);

        // RT.debug("Named-import scope", "scope", tree.namedImportScope);
        // RT.debug("Star-import scope", "scope", tree.starImportScope);
        super.visitTopLevel(tree);
    }

    @Override public void visitClassDef (JCClassDecl tree) {
        RT.debug("Entering class '" + tree.name + "'", "sym", tree.sym);

        // if we're visiting an anonymous inner class, we have to create a bogus scope as javac
        // does not Enter anonymous inner classes during the normal Enter phase; we currently don't
        // end up using this scope because ASTUtil.isLibraryOverrider requires substantially more
        // context to be in place than we want to set up manually (and Types.closure() caches class
        // symbols which we wouldn't want it to do with the fake ClassSymbol we need to create
        // here), so for now we cope with a bogus scope and some hackery
        Scope nscope = (tree.sym != null) ? tree.sym.members_field.dupUnshared() :
            new Scope(new ClassSymbol(0, tree.name, _env.enclMethod.sym));

        // note the environment of the class we're processing
        Env<DetypeContext> oenv = _env;
        // _env = _env.dup(tree, oenv.info.dup(new Scope(tree.sym)));
        _env = _env.dup(tree, oenv.info.dup(nscope));
        _env.enclClass = tree;
        _env.outer = oenv;

        if (tree.name != _names.empty) {
            // add our @Transformed annotation to the AST
            JCAnnotation a = _tmaker.Annotation(
                mkFA(Transformed.class.getName(), tree.pos), List.<JCExpression>nil());
            // a.pos = tree.pos; // maybe we want to provide a fake source position?
            tree.mods.annotations = tree.mods.annotations.prepend(a);

            // since the annotations AST has already been resolved into type symbols, we have to
            // manually add a type symbol for annotation to the ClassSymbol
            tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                Backdoor.enterAnnotation(_annotate, a, _syms.annotationType,
                                         _enter.getEnv(tree.sym)));
        }

        _clstack = _clstack.prepend(tree);
        super.visitClassDef(tree);
        _clstack = _clstack.tail;
        _env = oenv;

        RT.debug("Leaving class " + tree.name);
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
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
            _env.info.scope.enter(new VarSymbol(0, tree.name, null, _env.info.scope.owner));
        }

        // we don't want to detype the param(s) of a catch block nor the arguments of a library
        // overriding method
        String path = path();
        if (!path.contains(".Catch") &&
            !(path.contains(".MethodDef.params") && inLibraryOverrider())) {
//                 RT.debug("Transforming vardef", "mods", tree.mods, "name", tree.name,
//                          "vtype", what(tree.vartype), "init", tree.init,
//                          "sym", ASTUtil.expand(tree.sym));
            tree.vartype = _tmaker.Ident(_names.fromString("Object"));
        }
    }

    @Override public void visitReturn (JCReturn tree) {
        super.visitReturn(tree);

        // if we're in a method whose signature cannot be transformed, we must cast the result of
        // the return type back to the static method return type
        if (tree.expr != null && inLibraryOverrider()) {
            tree.expr = callRT("checkedCast", tree.expr.pos,
                               classLiteral(_env.enclMethod.restype, tree.expr.pos), tree.expr);
        }
    }

    @Override public void visitUnary (JCUnary tree) {
        super.visitUnary(tree);

        JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
        result = callRT("unop", opcode.pos, opcode, tree.arg);
        // RT.debug("Rewrote unop", "kind", tree.getKind(), "tp", tree.pos, "ap", opcode.pos);
    }

    @Override public void visitBinary (JCBinary tree) {
        super.visitBinary(tree);

        JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
        result = callRT("binop", tree.pos, opcode, tree.lhs, tree.rhs);
        // RT.debug("Rewrote binop", "kind", tree.getKind(), "tp", tree.pos, "ap", opcode.pos);
    }

    @Override public void visitNewClass (JCNewClass that) {
//         RT.debug("Class instantiation", "typeargs", that.typeargs, "class", what(that.clazz),
//                  "args", that.args);

        // if we see an anonymous inner class declaration, resolve the type of the to-be-created
        // class, we need this in inLibraryOverrider() for our approximation approach
        Symbol oanonp = _env.info.anonParent;
        if (that.def != null) {
            Name cname = (that.clazz instanceof JCTypeApply) ?
                TreeInfo.fullName(((JCTypeApply)that.clazz).clazz) : TreeInfo.fullName(that.clazz);
            _env.info.anonParent = Resolver.findType(_env, cname);
            if (_env.info.anonParent == null) {
                RT.debug("Pants! Unable to resolve type of anonymous inner parent", "name", cname);
            }

// TODO: we don't absolutely need this right now but eventually we'll probably want it
//             // we need also to do our own partial Enter on this anonymous class so that the class
//             // and its methods have sufficient symbol information to keep everything working
//             that.def.accept(new TreeScanner() {
//                 @Override public void visitClassDef (JCClassDecl tree) {
//                     if (tree.sym != null) {
//                         throw new IllegalStateException("Entering already entered tree? " + tree);
//                     }

//                     ClassSymbol osym = new ClassSymbol(0, tree.name, _env.enclMethod.sym);
//                 }

//                 @Override public void visitMethodDef (JCMethodDecl tree) {
//                 }
//             });
        }
        super.visitNewClass(that);
        _env.info.anonParent = oanonp;

// TODO: we can't reflectively create anonymous inner classes so maybe we should not detype
// constructor invocation, but rather directly inject the extra type tag arguments...

//         // if there is a specific enclosing instance provided, use that, otherwise use this
//         // unless we're in a static context in which case use nothing
//         List<JCExpression> args;
//         if (that.encl != null) {
//             args = that.args.prepend(that.encl);
//         } else if (inStatic()) {
//             args = that.args.prepend(_tmaker.Literal(TypeTags.BOT, null));
//         } else {
//             args = that.args.prepend(_tmaker.Ident(_names._this));
//         }
//         args = args.prepend(classLiteral(that.clazz, that.clazz.pos));

//         JCMethodInvocation invoke = callRT("newInstance", that.pos, args);
//         invoke.varargsElement = that.varargsElement;
//         result = invoke;
    }

    @Override public void visitNewArray (JCNewArray tree) {
        super.visitNewArray(tree);

        RT.debug("Array creation", "expr", tree, "dims", tree.dims, "elems", tree.elems);

        if (tree.elemtype == null) {
            RT.debug("Hrm? " + tree);
            return;
        }

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
            // RT.debug("Transformed select " + tree + " (" + path + ")");
        } else {
            RT.debug("!!! Not xforming select: " + tree + " (" + path + ")");
        }
    }

    @Override public void visitApply (JCMethodInvocation tree) {
        super.visitApply(tree);

//             RT.debug("Method invocation", "typeargs", tree.typeargs, "method", what(tree.meth),
//                      "args", tree.args, "varargs", tree.varargsElement);

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
            Symbol nsym = Resolver.findMethod(_env, mfid.name);
            if (nsym == null) {
                RT.debug("!!! Not transforming unresolvable method: " + tree);
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
//                     RT.debug("Mutated", "typeargs", tree.typeargs, "method", what(tree.meth),
//                              "args", tree.args, "varargs", tree.varargsElement);

        // are there other types of invocations?
        } else {
            RT.debug("Unknown invocation?", "typeargs", tree.typeargs,
                     "method", what(tree.meth), "args", tree.args,
                     "varargs", tree.varargsElement);
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

    @Override public void visitForLoop (JCForLoop tree) {
        super.visitForLoop(tree);

        // we need to cast the for condition expression to boolean
        tree.cond = callRT("asBoolean", tree.cond.pos, tree.cond);
    }

    @Override public void visitForeachLoop (JCEnhancedForLoop tree) {
        super.visitForeachLoop(tree);

        // rewrite the foreach loop as: foreach (iter : RT.asIterable(expr))
        tree.expr = callRT("asIterable", tree.expr.pos, tree.expr);
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
        _types = Types.instance(ctx);
        _enter = Enter.instance(ctx);
        _memberEnter = MemberEnter.instance(ctx);
        _attr = Attr.instance(ctx);
        // _names = Name.Table.instance(ctx);
        _names = Names.instance(ctx);
        _reader = ClassReader.instance(ctx);
        _syms = Symtab.instance(ctx);
        _annotate = Annotate.instance(ctx);
        _rootmaker = TreeMaker.instance(ctx);
    }

    protected boolean inLibraryOverrider ()
    {
        // if we're passed a method from an anonymous inner class, it will have no symbol
        // information and we currently instead fall back on a hack that marks all methods in the
        // inner class as detypable or not based on whether the parent of the anonymous inner class
        // is a library class or not; this is not strictly correct, but strict correctness is going
        // to be a huge pile of trouble that we want to make sure is worth it first
        if (_env.info.anonParent != null) {
            // TODO: is this going to think an inner-class/interface defined in this class but not
            // yet processed by the detyper is in fact a library class/interface? sigh...
            return ASTUtil.isLibrary(_env.info.anonParent);
        }

        // TODO: make this more correct: only match public static methods named 'main' with a
        // single String[] argument
        return _env.enclMethod.getName().toString().equals("main") ||
            ASTUtil.isLibraryOverrider(_types, _env.enclMethod.sym);
    }

    protected boolean isStaticReceiver (JCExpression fa)
    {
        // if we've already transformed the receiver, it will now be a method invocation
        if (!(fa instanceof JCIdent || fa instanceof JCFieldAccess)) {
            return false;
        }

        if (isScopedReference(fa)) {
            return false;
        }

        Name fname = TreeInfo.fullName(fa);
        if (Resolver.findType(_env, fname) != null || isQualifiedType(fa)) {
            return true;
        }
        if (isClassPlusSelect(fa)) {
            return false;
        }
        if (_reader.loadClass(fname) != null) {
            return true;
        }
        return false;
    }

    protected boolean isScopedReference (JCExpression expr)
    {
        if (expr instanceof JCFieldAccess) {
            return isScopedReference(((JCFieldAccess)expr).selected);

        } else if (expr instanceof JCIdent) {
            Name name = ((JCIdent)expr).name;
            // 'this' is always a reference (for our purposes)
            return name == _names._this || (Resolver.findVar(_env, name) != null);

        } else {
            RT.debug("isScopedReference on weird expr: " + expr);
            return false;
        }
    }

    protected boolean isQualifiedType (JCExpression expr)
    {
        if (expr instanceof JCFieldAccess) {
            JCFieldAccess fa = (JCFieldAccess)expr;
            return isQualifiedType(fa.selected) && (Resolver.findType(_env, fa.name) != null);
        } else if (expr instanceof JCIdent) {
            return (Resolver.findType(_env, ((JCIdent)expr).name) != null);
        } else {
            RT.debug("isQualifiedType called with weird expr: " + expr);
            return false;
        }
    }

    protected boolean isClassPlusSelect (JCExpression expr)
    {
        if (expr instanceof JCFieldAccess && isClassPlusSelect(((JCFieldAccess)expr).selected)) {
            return true;
        }
        return (Resolver.findType(_env, TreeInfo.fullName(expr)) != null);
    }

    protected boolean inStatic () {
        return (_env.enclMethod == null) ||
            (_env.enclMethod.mods != null && (_env.enclMethod.mods.flags & Flags.STATIC) != 0);
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

    protected List<JCClassDecl> _clstack = List.nil();

    protected Types _types;
    // protected Name.Table _names;
    protected Names _names;
    protected ClassReader _reader;
    protected Enter _enter;
    protected MemberEnter _memberEnter;
    protected Attr _attr;
    protected Symtab _syms;
    protected Annotate _annotate;
    protected TreeMaker _rootmaker;

    protected static final Context.Key<Detype> DETYPE_KEY = new Context.Key<Detype>();
}
