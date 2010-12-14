//
// $Id$

package org.ductilej.detyper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;

import org.ductilej.runtime.Debug;
import org.ductilej.util.ASTUtil;

/**
 * The main entry point for the detyping processor.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({Processor.SHOWCLASS_ARG, Processor.WRITECLASS_ARG, Processor.DEBUG_ARG,
                   Processor.WARNINGS_ARG, Processor.KEEPIFCS_ARG})
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

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);
        _detype = Detype.instance(ctx);

        // note our options
        _showclass = "true".equalsIgnoreCase(procenv.getOptions().get(SHOWCLASS_ARG));
        _writeclass = "true".equalsIgnoreCase(procenv.getOptions().get(WRITECLASS_ARG));
        Debug.DEBUG = "true".equalsIgnoreCase(procenv.getOptions().get(DEBUG_ARG));
        Resolver.WARNINGS = "true".equalsIgnoreCase(procenv.getOptions().get(WARNINGS_ARG));
        Detype.KEEPIFCS = "true".equalsIgnoreCase(procenv.getOptions().get(KEEPIFCS_ARG));
        Detype.COERCEALL = "true".equalsIgnoreCase(procenv.getOptions().get(COERCEALL_ARG));

        Debug.log("Detyper running", "vers", procenv.getSourceVersion());
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        // peskily, we'll get an Element for every top-level class in a compilation unit, but we
        // only want to process each compilation unit once, so we have to manually consolidate
        List<JCCompilationUnit> units = new ArrayList<JCCompilationUnit>();
        for (Element elem : roundEnv.getRootElements()) {
            if (elem instanceof PackageElement) {
                continue; // nothing to do for package elements
            }
            JCCompilationUnit unit = toUnit(elem);
            if (unit == null) {
                Debug.warn("Unable to obtain compilation unit for element", "elem", elem);
                continue;
            }
            // Debug.temp("Root elem " + elem, "unit", unit.getClass().getSimpleName());
            if (!units.contains(unit)) {
                units.add(unit);
            }
        }

        for (JCCompilationUnit unit : units) {
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

    // -Aorg.ductilej.debug=true causes debug log messages to be printed
    protected static final String DEBUG_ARG = "org.ductilej.debug";
    // -Aorg.ductilej.showclass=true causes classes to be printed after detyping
    protected static final String SHOWCLASS_ARG = "org.ductilej.showclass";
    // -Aorg.ductilej.writeclass=true causes classes to be written to a .djava file after detyping
    protected static final String WRITECLASS_ARG = "org.ductilej.writeclass";
    // -Aorg.ductilej.warnings=true causes warnings to be printed for unresolvable symbols
    protected static final String WARNINGS_ARG = "org.ductilej.warnings";
    // -Aorg.ductilej.keepifcs=true disables removal of interface method declarations (TEMP)
    protected static final String KEEPIFCS_ARG = "org.ductilej.keepifcs";
    // -Aorg.ductilej.coerceall=true makes primitive narrowing and format coercions implicit
    protected static final String COERCEALL_ARG = "org.ductilej.coerceall";
}
