//
// $Id$

package org.typelessj.detyper;

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
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
// import com.sun.tools.javac.util.Name; // Name.Table -> Names in OpenJDK
import com.sun.tools.javac.util.Names;

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

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);
        _types = Types.instance(ctx);
        _enter = Enter.instance(ctx);
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
            unit.accept(new DetypingVisitor(_rootmaker.forToplevel(unit)));
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
        public DetypingVisitor (TreeMaker maker) {
            _tmaker = maker;
        }

        @Override public void visitClassDef (JCClassDecl tree) {

            // add our @Transformed annotation to the AST
            JCAnnotation a = _tmaker.Annotation(
                mkFA(Transformed.class.getName()), List.<JCExpression>nil());
            // a.pos = tree.pos; // maybe we want to provide a fake source position?
            tree.mods.annotations = tree.mods.annotations.prepend(a);

            // since the annotations AST has already been resolved into type symbols, we have to
            // manually add a type symbol for annotation to the ClassSymbol
            tree.sym.attributes_field = tree.sym.attributes_field.prepend(
                _annotate.enterAnnotation(a, _syms.annotationType, _enter.getEnv(tree.sym)));
            // TODO: Annotate.enterAnnotation is non-public, whee!

            RT.debug("Entering class '" + tree.name + "'");
            super.visitClassDef(tree);
            RT.debug("Leaving class " + tree.name);
//             RT.debug(""+tree);
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
        }

        @Override public void visitBinary (JCBinary tree) {
            super.visitBinary(tree);

            JCLiteral opcode = _tmaker.Literal(TypeTags.CLASS, tree.getKind().toString());
            JCMethodInvocation apply = _tmaker.Apply(
                null, mkRT("op"), List.<JCExpression>of(opcode, tree.lhs, tree.rhs));
            apply.pos = tree.pos;
//             RT.debug("Rewrote binop", "kind", tree.getKind(), "pos", tree.pos, "apos", apply.pos);
            result = apply;
        }

        @Override public void visitApply (JCMethodInvocation that) {
            RT.debug("Method invocation", "typeargs", that.typeargs, "method", what(that.meth),
                     "args", that.args, "varargs", that.varargsElement);

            // convert expr.method(args) into RT.invoke("method", expr, args)
            if (that.meth instanceof JCFieldAccess) {
                JCFieldAccess mfacc = (JCFieldAccess)that.meth;
                that.args = that.args.prepend(mfacc.selected).
                    prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                that.meth = mkRT("invoke");

                RT.debug("Mutated", "typeargs", that.typeargs, "method", what(that.meth),
                         "args", that.args, "varargs", that.varargsElement);

            // convert method(args) into RT.invoke("method", this, args)
            } else if (that.meth instanceof JCIdent) {
                RT.debug("Did not mutate", "typeargs", that.typeargs, "method", what(that.meth),
                         "args", that.args, "varargs", that.varargsElement);

            // are there other types of invocations?
            } else {
                RT.debug("Unknown invocation?", "typeargs", that.typeargs,
                         "method", what(that.meth), "args", that.args,
                         "varargs", that.varargsElement);
            }

            super.visitApply(that);
        }

        protected JCFieldAccess mkRT (String method) {
            return _tmaker.Select(mkFA(RT.class.getName()), _names.fromString(method));
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

        protected TreeMaker _tmaker;
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
    protected Names _names;
    protected Enter _enter;
    protected Symtab _syms;
    protected Annotate _annotate;
    protected TreeMaker _rootmaker;
}
