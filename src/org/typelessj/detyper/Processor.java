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
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name; // Name.Table -> Names in OpenJDK

import org.typelessj.runtime.RT;
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

        _trees = Trees.instance(procenv);
        _types = Types.instance(((JavacProcessingEnvironment)procenv).getContext());
        _names = Name.Table.instance(((JavacProcessingEnvironment)procenv).getContext());
        _rootmaker = TreeMaker.instance(((JavacProcessingEnvironment)procenv).getContext());
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
            RT.debug("Root elem " + elem, "unit", unit.getClass().getSimpleName(),
                     "sym.mems", ASTUtil.expand(unit.packge.members_field.elems.sym));
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
            RT.debug("Entering class " + tree.name);
            super.visitClassDef(tree);
            RT.debug("Leaving class " + tree.name);
            RT.debug(""+tree);
        }

        @Override public void visitVarDef (JCVariableDecl tree) {
            RT.debug("Transforming vardef", "mods", tree.mods, "name", tree.name,
                     "vtype", what(tree.vartype), "init", tree.init,
                     "sym", ASTUtil.expand(tree.sym));

            // TODO: don't do this if we're in a library method
            tree.vartype = _tmaker.Ident(_names.fromString("Object"));

            super.visitVarDef(tree);
        }

        @Override public void visitMethodDef (JCMethodDecl tree) {
            // no call to super, as we need more control over what is transformed

            RT.debug("Method decl", "name", tree.getName(), "sym", tree.sym,
                     "isOverride", ASTUtil.isOverrider(_types, tree.sym),
                     "restype", what(tree.restype), "params", tree.params);

            boolean mainHack = tree.getName().toString().equals("main");

            // TODO: also check whether this method implements a library interface
            if (!mainHack && !ASTUtil.isOverrider(_types, tree.sym)) {
                // transform the return type if it is not void
                if (tree.restype != null && !ASTUtil.isVoid(tree.restype)){
                    RT.debug("Transforming return type", "name", tree.getName(),
                             "rtype", tree.restype);
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
            RT.debug("Rewrote binop", "kind", tree.getKind(), "pos", tree.pos, "apos", apply.pos);
            result = apply;
        }

        @Override public void visitApply (JCMethodInvocation that) {
//             RT.debug("Method invocation", "typeargs", that.typeargs, "method", what(that.meth),
//                      "args", that.args, "varargs", that.varargsElement);

            // TODO: we're only transforming methods that look like foo.bar for now, this misses
            // method calls with implicit receivers for which that.meth is a JCIdent node
            if (that.meth instanceof JCFieldAccess) {
                JCFieldAccess mfacc = (JCFieldAccess)that.meth;
                // convert expr.method(args) into RT.invoke("method", expr, args)
                that.args = that.args.prepend(mfacc.selected).
                    prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                that.meth = mkRT("invoke");

                RT.debug("Mutated", "typeargs", that.typeargs, "method", what(that.meth),
                         "args", that.args, "varargs", that.varargsElement);
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
    protected Name.Table _names;
    protected TreeMaker _rootmaker;
}
