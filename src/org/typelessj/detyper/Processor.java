//
// $Id$

package org.typelessj.detyper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;

import org.typelessj.runtime.Debug;
import org.typelessj.util.ASTUtil;

/**
 * The main entry point for the detyping processor.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({Processor.SHOWCLASS_ARG, Processor.WRITECLASS_ARG, Processor.DEBUG_ARG})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
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

        if (!Backdoor.init(procenv)) {
            return;
        }

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);
        _detype = Detype.instance(ctx);

        // note our options
        _showclass = "true".equalsIgnoreCase(procenv.getOptions().get(SHOWCLASS_ARG));
        _writeclass = "true".equalsIgnoreCase(procenv.getOptions().get(WRITECLASS_ARG));
        Debug.debug = "true".equalsIgnoreCase(procenv.getOptions().get(DEBUG_ARG));

        Debug.log("Detyper running", "vers", procenv.getSourceVersion());
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            JCCompilationUnit unit = toUnit(elem);
//             Debug.log("Root elem " + elem, "unit", unit.getClass().getSimpleName(),
//                       "sym.mems", ASTUtil.expand(unit.packge.members_field.elems.sym));
            _detype.detype(unit);
            if (_showclass) {
                System.out.println(""+unit);
            }
            if (_writeclass) {
                File dout = new File(unit.getSourceFile().getName().replace(".java", ".djava"));
                try {
                    PrintWriter out = new PrintWriter(new FileWriter(dout));
                    out.print(unit);
                    out.close();
                } catch (Exception e) {
                    Debug.warn("Failure writing detyped output", "file", dout, "error", e);
                }
            }
        }
        return false;
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected Trees _trees;
    protected Detype _detype;
    protected boolean _showclass;
    protected boolean _writeclass;

    // -Aorg.typelessj.debug=true causes debug log messages to be printed
    protected static final String DEBUG_ARG = "org.typelessj.debug";
    // -Aorg.typelessj.showclass=true causes classes to be printed after detyping
    protected static final String SHOWCLASS_ARG = "org.typelessj.showclass";
    // -Aorg.typelessj.writeclass=true causes classes to be written to a .djava file after detyping
    protected static final String WRITECLASS_ARG = "org.typelessj.writeclass";
}
