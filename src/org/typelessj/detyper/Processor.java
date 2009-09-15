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
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

import com.samskivert.util.LogBuilder;

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
        debug("Detyper running", "vers", procenv.getSourceVersion());
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            JCTree.JCCompilationUnit unit = toUnit(elem);
            debug("Root elem " + elem, "unit", unit.getClass().getSimpleName());
            unit.accept(new MutatorVisitor());
        }
        return false;
    }

    protected JCTree.JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCTree.JCCompilationUnit)path.getCompilationUnit();
    }

    protected static class MutatorVisitor extends TreeTranslator
    {
        @Override public void visitApply (JCTree.JCMethodInvocation that)
        {
            debug("Method invocation", "typeargs", that.typeargs, "method", that.meth,
                  "mtype", that.meth.getClass().getSimpleName(),
                  "args", that.args, "varargs", that.varargsElement);
//             if (that.meth instanceof JCTree.JCIdent) {
//                 JCTree.JCIdent mident = (JCTree.JCIdent)that.meth;
//                 mident.name = mident.name.table.fromString(mident.name + "Oops");
//             }
            super.visitApply(that);
        }
    }

    protected static void debug (String message, Object... args)
    {
        System.out.println(new LogBuilder(message, args));
    }

    protected Trees _trees;
}
