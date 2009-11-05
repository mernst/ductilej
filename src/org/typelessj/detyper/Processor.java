//
// $Id$

package org.typelessj.detyper;

import java.lang.reflect.Method;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
// import com.sun.tools.javac.util.Name; // Name.Table -> Names in OpenJDK
import com.sun.tools.javac.util.Names;

import org.typelessj.runtime.RT;
import org.typelessj.runtime.Transformed;
import org.typelessj.util.ASTUtil;
import org.typelessj.util.PathedTreeTranslator;

/**
 * The main entry point for the detyping processor.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class Processor extends AbstractProcessor
{
    @Override // from AbstractProcessor
    public void init (ProcessingEnvironment procenv)
    {
        super.init(procenv);

        if (!(procenv instanceof JavacProcessingEnvironment)) {
            procenv.getMessager().printMessage(
                Diagnostic.Kind.WARNING, "Detyper requires javac v1.6.");
            return;
        }

        try {
            for (Method m : Annotate.class.getDeclaredMethods()) {
                if (m.getName().equals("enterAnnotation")) {
                    _enterAnnotation = m;
                }
            }
            if (_enterAnnotation == null) {
                procenv.getMessager().printMessage(
                    Diagnostic.Kind.WARNING, "Unable to locate Annotate.enterAnnotation method.");
                return;
            }
            _enterAnnotation.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            procenv.getMessager().printMessage(
                Diagnostic.Kind.WARNING, "Unable to access Annotate.enterAnnotation method.");
            return;
        }

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);
        _types = Types.instance(ctx);
        _enter = Enter.instance(ctx);
        _attr = Attr.instance(ctx);
        // _names = Name.Table.instance(ctx);
        _names = Names.instance(ctx);
        _syms = Symtab.instance(ctx);
        _annotate = Annotate.instance(ctx);
        _rootmaker = TreeMaker.instance(ctx);
        RT.debug("Detyper running", "vers", procenv.getSourceVersion());
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            JCCompilationUnit unit = toUnit(elem);
//             RT.debug("Root elem " + elem, "unit", unit.getClass().getSimpleName(),
//                      "sym.mems", ASTUtil.expand(unit.packge.members_field.elems.sym));
            unit.accept(new DetypingVisitor(unit));
            if (true || Boolean.getBoolean("showclass")) {
                RT.debug(""+unit);
            }
        }
        return false;
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected class DetypingVisitor extends PathedTreeTranslator
    {
        public DetypingVisitor (JCCompilationUnit unit) {
            _unit = unit;
            _tmaker = _rootmaker.forToplevel(unit);
            _vizcls = ASTUtil.enumVisibleClassNames(unit);

            // RT.debug("Visible classes " + _vizcls);
        }

        @Override public void visitClassDef (JCClassDecl tree) {
            RT.debug("Entering class '" + tree.name + "'");

            if (tree.name != _names.empty) {
                // add our @Transformed annotation to the AST
                JCAnnotation a = _tmaker.Annotation(
                    mkFA(Transformed.class.getName()), List.<JCExpression>nil());
                // a.pos = tree.pos; // maybe we want to provide a fake source position?
                tree.mods.annotations = tree.mods.annotations.prepend(a);

                // since the annotations AST has already been resolved into type symbols, we have to
                // manually add a type symbol for annotation to the ClassSymbol
                tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                    enterAnnotation(a, _syms.annotationType, _enter.getEnv(tree.sym)));
            }

            _clstack = _clstack.prepend(tree);
            super.visitClassDef(tree);
            _clstack = _clstack.tail;

            RT.debug("Leaving class " + tree.name);
        }

        @Override public void visitVarDef (JCVariableDecl tree) {
//             RT.debug("Transforming vardef", "mods", tree.mods, "name", tree.name,
//                      "vtype", what(tree.vartype), "init", tree.init,
//                      "sym", ASTUtil.expand(tree.sym));

            // TODO: is not calling translate(tree.params) all we need to do to ensure that we
            // don't detype parameters to library method overriders and implementers?
            tree.vartype = _tmaker.Ident(_names.fromString("Object"));

            super.visitVarDef(tree);
        }

        @Override public void visitMethodDef (JCMethodDecl tree) {
            // no call to super, as we need more control over what is transformed

            JCMethodDecl ometh = _curmeth;
            _curmeth = tree; // note the current method being processed

            if (tree.sym == null) {
                RT.debug("Zoiks, no symbol", "tree", tree); // TODO: is anonymous inner class?
            }

//             RT.debug("Method decl", "name", tree.getName(), "sym", tree.sym,
//                      "isOverride", ASTUtil.isLibraryOverrider(_types, tree.sym),
//                      "restype", what(tree.restype), "params", tree.params);

            // TODO: extract this into an ASTUtil method, use a tree traverser to make it correct:
            // only match public static methods named 'main' with a single String[] argument
            boolean mainHack = tree.getName().toString().equals("main");

            if (!mainHack && !ASTUtil.isLibraryOverrider(_types, tree.sym)) {
                // transform the return type if it is not void
                if (tree.restype != null && !ASTUtil.isVoid(tree.restype)){
//                     RT.debug("Transforming return type", "name", tree.getName(),
//                              "rtype", tree.restype);
                    tree.restype = _tmaker.Ident(_names.fromString("Object"));
                }

                // transform the method parameters
                tree.params = translateVarDefs(tree.params);
            }

            // we always translate these bits
            tree.mods = translate(tree.mods);
            tree.typarams = translateTypeParams(tree.typarams);
            tree.thrown = translate(tree.thrown);
            tree.body = translate(tree.body);
            result = tree;

            _curmeth = ometh; // we're no longer processing this method
        }

        @Override public void visitReturn (JCReturn tree) {
            super.visitReturn(tree);

            // if we're in a method whose signature cannot be transformed, we must cast the result
            // of the return type back to the static method return type
            if (ASTUtil.isLibraryOverrider(_types, _curmeth.sym)) {
                tree.expr = callRT("checkedCast", tree.expr.pos,
                                   classLiteral(_curmeth.restype, tree.expr.pos), tree.expr);
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
            super.visitNewClass(that);

            RT.debug("Class instantiation", "typeargs", that.typeargs, "class", what(that.clazz),
                     "args", that.args);

            // if there is a specific enclosing instance provided, use that, otherwise use this
            // unless we're in a static context in which case use nothing
            List<JCExpression> args;
            if (that.encl != null) {
                args = that.args.prepend(that.encl);
            } else if (inStatic()) {
                args = that.args.prepend(_tmaker.Literal(TypeTags.BOT, null));
            } else {
                args = that.args.prepend(_tmaker.Ident(_names._this));
            }
            args = args.prepend(classLiteral(that.clazz, that.clazz.pos));

            JCMethodInvocation invoke = callRT("newInstance", that.pos, args);
            invoke.varargsElement = that.varargsElement;
            result = invoke;
        }

        @Override public void visitNewArray (JCNewArray tree) {
            super.visitNewArray(tree);

            RT.debug("Array creation", "dims", tree.dims, "elems", tree.elems);
            // TODO
        }

// TODO: this is fiddlier
//         @Override public void visitThrow (JCThrow tree) {
//             super.visitThrow(tree);

//             // add a cast to Throwable on the expression being thrown since we will have detyped it
//             tree.expr = _tmaker.TypeCast(_tmaker.Ident(_names.fromString("Throwable")), tree.expr);
//         }

        @Override public void visitSelect (JCFieldAccess tree) {
            super.visitSelect(tree);

            // TODO: more may be needed here
            String path = path();
            boolean wantXform = true;
            wantXform = wantXform && !path.contains(".TopLevel.pid");
            wantXform = wantXform && !path.contains(".Import");
            wantXform = wantXform && !path.contains(".Annotation");
            wantXform = wantXform && !path.contains(".Apply.meth");
            wantXform = wantXform && !path.contains(".VarDef.vartype");
            wantXform = wantXform && !path.contains(".NewClass.clazz");
            // TODO: this is pesky, better to say we must be in ClassDef.defs but then we need to
            // deal with inner classes... blah
            wantXform = wantXform && !path.contains(".ClassDef.typarams");
            wantXform = wantXform && !path.contains(".ClassDef.extending");
            wantXform = wantXform && !path.contains(".ClassDef.implementing");

            if (wantXform && !_vizcls.contains(tree.selected.toString())) {
                // transform obj.field into RT.select(obj, "field")
                result = callRT("select", tree.pos,
                                _tmaker.Literal(TypeTags.CLASS, tree.name.toString()),
                                tree.selected);
                RT.debug("Transformed select " + tree + " (" + path + ")");
            }
        }

        @Override public void visitApply (JCMethodInvocation tree) {
            super.visitApply(tree);

//             RT.debug("Method invocation", "typeargs", tree.typeargs, "method", what(tree.meth),
//                      "args", tree.args, "varargs", tree.varargsElement);

// freaks out: possibly wrong environment; possibly wrong compiler state
//             RT.debug("Method type", "meth", _attr.attribExpr(
//                          tree.meth, _enter.getEnv(_curclass.sym), Type.noType));

            // transform expr.method(args)
            if (tree.meth instanceof JCFieldAccess) {
                JCFieldAccess mfacc = (JCFieldAccess)tree.meth;

                // we need to determine if 'expr' identifies a class in which case we need to make
                // a static method invocation on tree class; because we have no concept of scope
                // and names at this point we have to fake things as best we can
                if (_vizcls.contains(mfacc.selected.toString())) {
                    // convert to RT.invokeStatic("method", mfacc.selected.class, args)
                    tree.args = tree.args.
                        prepend(classLiteral(mfacc.selected, mfacc.selected.pos)).
                        prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                    tree.meth = mkRT("invokeStatic", mfacc.pos);
                } else {
                    // convert to RT.invoke("method", expr, args)
                    tree.args = tree.args.prepend(mfacc.selected).
                        prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                    tree.meth = mkRT("invoke", mfacc.pos);
                }

//                 RT.debug("Mutated", "typeargs", tree.typeargs, "method", what(mfacc),
//                          "type", tree.type, "args", tree.args, "varargs", tree.varargsElement);

            // transform method(args)
            } else if (tree.meth instanceof JCIdent) {
                // TODO: all of the following needs to handle static imports
                JCIdent mfid = (JCIdent)tree.meth;
                if ("super".equals(mfid.toString())) {
                    // super() cannot be transformed so we leave it alone

                // we're in a static method, so a receiverless method invocation must also be a
                // static method, but we need to find out what class to call it on
                } else if (inStatic()) {
                    // find the closest class tree defines this method
                    JCClassDecl decl = null;
                    for (List<JCClassDecl> cl = _clstack; !cl.isEmpty(); cl = cl.tail) {
                        if (ASTUtil.definesStaticMethod(cl.head, tree)) {
                            decl = cl.head;
                            break;
                        }
                    }
                    if (decl == null) {
                        RT.debug("Cannot find declaration of static method call?",
                                 "method", what(tree.meth));
                    } else {
                        // convert to RT.invokeStatic("method", decl.class, args)
                        tree.args = tree.args. // TODO: better pos than tree.pos
                            prepend(classLiteral(_tmaker.Ident(decl.name), tree.pos)).
                            prepend(_tmaker.Literal(TypeTags.CLASS, mfid.toString()));
                        tree.meth = mkRT("invokeStatic", mfid.pos);
                    }

                } else {
                    // we're not in a static so we get RT.invoke("method", this, args) and at
                    // runtime we'll sort out whether they meant to call a static or non-static
                    // method (in the latter case we just ignore this)
                    JCIdent recid = _tmaker.Ident(_names._this);
                    recid.pos = mfid.pos;
                    tree.args = tree.args.prepend(recid).
                        prepend(_tmaker.Literal(TypeTags.CLASS, mfid.toString()));
                    tree.meth = mkRT("invoke", mfid.pos);
                    RT.debug("Mutated", "typeargs", tree.typeargs, "method", what(tree.meth),
                             "args", tree.args, "varargs", tree.varargsElement);
                }

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

            String path = path();
            // TODO: this is not quite correct because we need to handle: foo[bar[ii]] = 1
            if (!path.contains(".Assign.lhs")) {
                // rewrite the array dereference as: RT.atIndex(array, index)
                result = callRT("atIndex", tree.pos, tree.indexed, tree.index);
            }
        }

        @Override public void visitAssign (JCAssign tree) {
            super.visitAssign(tree);

            // TODO: we need to handle (foo[ii]) = 1 and maybe (a ? foo[ii] : bar[ii]) = 1
            if (tree.lhs instanceof JCArrayAccess) {
                JCArrayAccess aa = (JCArrayAccess)tree.lhs;
                result = callRT("assignAt", tree.pos, aa.indexed, aa.index, tree.rhs);
            }
        }

        protected boolean inStatic () {
            return (_curmeth == null) ||
                (_curmeth.mods != null && (_curmeth.mods.flags & Flags.STATIC) != 0);
        }

        protected JCMethodInvocation callRT (String method, int pos, JCExpression... args) {
            return setPos(_tmaker.Apply(null, mkRT(method, pos), List.from(args)), pos);
        }

        protected JCMethodInvocation callRT (String method, int pos, List<JCExpression> args) {
            return setPos(_tmaker.Apply(null, mkRT(method, pos), args), pos);
        }

        protected JCFieldAccess mkRT (String method, int pos) {
            return setPos(_tmaker.Select(mkFA(RT.class.getName()), _names.fromString(method)), pos);
        }

        protected JCExpression mkFA (String fqName) {
            int didx = fqName.lastIndexOf(".");
            if (didx == -1) {
                return _tmaker.Ident(_names.fromString(fqName)); // simple identifier
            } else {
                return _tmaker.Select(mkFA(fqName.substring(0, didx)), // nested FA expr
                                      _names.fromString(fqName.substring(didx+1)));
            }
        }

        protected JCExpression classLiteral (JCExpression expr, int pos) {
            // TODO: validate that we got passed either Name or package.Name
            if ("int".equals(expr.toString())) {
                expr = _tmaker.Ident(_names.fromString("Integer"));
            }
            return setPos(_tmaker.Select(expr, _names._class), pos);
        }

        protected JCCompilationUnit _unit;
        protected TreeMaker _tmaker;
        protected Set<String> _vizcls;

        protected List<JCClassDecl> _clstack = List.nil();
        protected JCMethodDecl _curmeth;
        protected boolean _curmethxf;
    }

    // Annotate.enterAnnotation is non-public so we have to be sneaky
    protected Attribute.Compound enterAnnotation (
        JCAnnotation a, Type expected, Env<AttrContext> env)
    {
        try {
            return (Attribute.Compound)_enterAnnotation.invoke(_annotate, a, expected, env);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    protected static <T extends JCTree> T setPos (T tree, int pos)
    {
        tree.pos = pos;
        return tree;
    }

    protected Trees _trees;
    protected Types _types;
    // protected Name.Table _names;
    protected Names _names;
    protected Enter _enter;
    protected Attr _attr;
    protected Symtab _syms;
    protected Annotate _annotate;
    protected TreeMaker _rootmaker;

    protected Method _enterAnnotation;
}
