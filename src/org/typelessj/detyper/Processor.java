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
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Name;

import org.typelessj.runtime.RT;

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
            RT.debug("Root elem " + elem, "unit", unit.getClass().getSimpleName());
            unit.accept(new MutatorVisitor(_rootmaker.forToplevel(unit)));
        }
        return false;
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected static class MutatorVisitor extends TreeTranslator
    {
        public MutatorVisitor (TreeMaker maker) {
            _tmaker = maker;
        }

        @Override public void visitApply (JCMethodInvocation that) {
            RT.debug("Method invocation", "typeargs", that.typeargs, "method", what(that.meth),
                     "args", that.args, "varargs", that.varargsElement);

            // TODO: we're only transforming methods that look like foo.bar for now, this misses
            // method calls with implicit receivers for which that.meth is a JCIdent node
            if (that.meth instanceof JCFieldAccess) {
                JCFieldAccess mfacc = (JCFieldAccess)that.meth;
                RT.debug("Decoded field access", "selected", what(mfacc.selected),
                         "name", mfacc.name, "symbol", mfacc.sym);
                // convert expr.method(args) into RT.invoke("method", expr, args)
                that.args = that.args.prepend(mfacc.selected).
                    prepend(_tmaker.Literal(TypeTags.CLASS, mfacc.name.toString()));
                that.meth = mkRT(mfacc.name.table, "invoke");

                RT.debug("Mutated", "typeargs", that.typeargs, "method", what(that.meth),
                         "args", that.args, "varargs", that.varargsElement);
            }

            super.visitApply(that);
        }

        protected JCFieldAccess mkRT (Name.Table table, String method) {
            return _tmaker.Select(mkFA(table, RT.class.getName()), table.fromString(method));
        }

        protected JCExpression mkFA (Name.Table table, String fqName) {
            int didx = fqName.lastIndexOf(".");
            if (didx == -1) {
                return _tmaker.Ident(table.fromString(fqName)); // simple identifier
            } else {
                return _tmaker.Select(mkFA(table, fqName.substring(0, didx)), // nested FA expr
                                      table.fromString(fqName.substring(didx+1)));
            }
        }

        protected TreeMaker _tmaker;
    }

    protected static String what (JCTree node)
    {
        return node.getClass().getSimpleName() + "[" + node + "]";
    }

    protected Trees _trees;
    protected TreeMaker _rootmaker;
}
