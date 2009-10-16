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

import com.sun.tools.javac.util.Name; // Name.Table -> Names in OpenJDK
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
// import com.sun.tools.javac.util.Names;

import org.typelessj.runtime.RT;
import org.typelessj.runtime.Transformed;
import org.typelessj.util.ASTUtil;

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
        _names = Name.Table.instance(ctx);
        // _names = Names.instance(ctx);
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
        }
        return false;
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected class DetypingVisitor extends TreeTranslator
    {
        public DetypingVisitor (JCCompilationUnit unit) {
            _unit = unit;
            _tmaker = _rootmaker.forToplevel(unit);
            _vizcls = ASTUtil.enumVisibleClassNames(unit);

            System.out.println("Visible classes " + _vizcls);
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
            if (Boolean.getBoolean("showclass")) {
                RT.debug(""+tree);
            }
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

            _curmeth = null; // we're no longer processing this method
        }

        @Override public void visitUnary (JCUnary tree) {
            super.visitUnary(tree);

            JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
            JCMethodInvocation apply = _tmaker.Apply(
                null, mkRT("unop", opcode.pos), List.<JCExpression>of(opcode, tree.arg));
            apply.pos = tree.pos;
//             RT.debug("Rewrote unop", "kind", tree.getKind(), "pos", tree.pos, "apos", apply.pos);

            result = apply;
        }

        @Override public void visitBinary (JCBinary tree) {
            super.visitBinary(tree);

            JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
            JCMethodInvocation apply = _tmaker.Apply(
                null, mkRT("binop", opcode.pos), List.<JCExpression>of(opcode, tree.lhs, tree.rhs));
            apply.pos = tree.pos;
//             RT.debug("Rewrote binop", "kind", tree.getKind(), "pos", tree.pos, "apos", apply.pos);
            result = apply;
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
            args = args.prepend(_tmaker.Select(that.clazz, _names._class));

            JCMethodInvocation invoke = _tmaker.Apply(
                List.<JCExpression>nil(), mkRT("newInstance", that.pos), args);
            invoke.varargsElement = that.varargsElement;
            invoke.pos = that.pos;

            result = invoke;
        }

        @Override public void visitApply (JCMethodInvocation that) {
            RT.debug("Method invocation", "typeargs", that.typeargs, "method", what(that.meth),
                     "args", that.args, "varargs", that.varargsElement);

// freaks out: possibly wrong environment; possibly wrong compiler state
//             RT.debug("Method type", "meth", _attr.attribExpr(
//                          that.meth, _enter.getEnv(_curclass.sym), Type.noType));

            // transform expr.method(args)
            if (that.meth instanceof JCFieldAccess) {
                JCFieldAccess mfacc = (JCFieldAccess)that.meth;

                // we need to determine if 'expr' identifies a class in which case we need to make
                // a static method invocation on that class; because we have no concept of scope
                // and names at this point we have to fake things as best we can
                if (_vizcls.contains(mfacc.selected.toString())) {
                    // convert to RT.invokeStatic("method", mfacc.selected.class, args)
                    that.args = that.args.
                        prepend(_tmaker.Select(mfacc.selected, _names._class)).
                        prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                    that.meth = mkRT("invokeStatic", mfacc.pos);
                } else {
                    // convert to RT.invoke("method", expr, args)
                    that.args = that.args.prepend(mfacc.selected).
                        prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                    that.meth = mkRT("invoke", mfacc.pos);
                }

                RT.debug("Mutated", "typeargs", that.typeargs, "method", what(that.meth),
                         "type", that.type, "args", that.args, "varargs", that.varargsElement);

            // transform method(args)
            } else if (that.meth instanceof JCIdent) {
                // TODO: all of the following needs to handle static imports
                JCIdent mfid = (JCIdent)that.meth;
                if ("super".equals(mfid.toString())) {
                    // super() cannot be transformed so we leave it alone

                // we're in a static method, so a receiverless method invocation must also be a
                // static method, but we need to find out what class to call it on
                } else if (inStatic()) {
                    // find the closest class that defines this method
                    JCClassDecl decl = null;
                    for (List<JCClassDecl> cl = _clstack; !cl.isEmpty(); cl = cl.tail) {
                        if (ASTUtil.definesStaticMethod(cl.head, that)) {
                            decl = cl.head;
                            break;
                        }
                    }
                    if (decl == null) {
                        RT.debug("Cannot find declaration of static method call?",
                                 "method", what(that.meth));
                    } else {
                        // convert to RT.invokeStatic("method", decl.class, args)
                        that.args = that.args.
                            prepend(_tmaker.Select(_tmaker.Ident(decl.name), _names._class)).
                            prepend(_tmaker.Literal(TypeTags.CLASS, mfid.toString()));
                        that.meth = mkRT("invokeStatic", mfid.pos);
                    }

                } else {
                    // we're not in a static so we get RT.invoke("method", this, args) and at
                    // runtime we'll sort out whether they meant to call a static or non-static
                    // method (in the latter case we just ignore this)
                    JCIdent recid = _tmaker.Ident(_names._this);
                    recid.pos = mfid.pos;
                    that.args = that.args.prepend(recid).
                        prepend(_tmaker.Literal(TypeTags.CLASS, mfid.toString()));
                    that.meth = mkRT("invoke", mfid.pos);
                    RT.debug("Mutated", "typeargs", that.typeargs, "method", what(that.meth),
                             "args", that.args, "varargs", that.varargsElement);
                }

            // are there other types of invocations?
            } else {
                RT.debug("Unknown invocation?", "typeargs", that.typeargs,
                         "method", what(that.meth), "args", that.args,
                         "varargs", that.varargsElement);
            }

            super.visitApply(that);
        }

        @Override public void visitIf (JCIf tree) {
            super.visitIf(tree);

            // we need to wrap the contents of all if expressions in a type-cast to boolean
            tree.cond = _tmaker.TypeCast(_tmaker.Ident(_names.fromString("Boolean")), tree.cond);
        }

        protected boolean inStatic () {
            return (_curmeth == null) ||
                (_curmeth.mods != null && (_curmeth.mods.flags & Flags.STATIC) != 0);
        }

        protected JCFieldAccess mkRT (String method, int pos) {
            JCFieldAccess fa = _tmaker.Select(mkFA(RT.class.getName()), _names.fromString(method));
            fa.pos = pos;
            return fa;
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

        protected JCCompilationUnit _unit;
        protected TreeMaker _tmaker;
        protected Set<String> _vizcls;

        protected List<JCClassDecl> _clstack = List.nil();
        protected JCMethodDecl _curmeth;
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

    protected Trees _trees;
    protected Types _types;
    protected Name.Table _names;
    // protected Names _names;
    protected Enter _enter;
    protected Attr _attr;
    protected Symtab _syms;
    protected Annotate _annotate;
    protected TreeMaker _rootmaker;

    protected Method _enterAnnotation;
}
