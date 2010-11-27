//
// $Id$

package org.ductilej.astviz;

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
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;

import org.ductilej.util.ASTUtil;

/**
 * The main entry point for the visualizing processor.
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
                Diagnostic.Kind.WARNING, "ASTVIZ requires javac v1.6.");
            return;
        }
        procenv.getMessager().printMessage(Diagnostic.Kind.NOTE, "ASTVIZ initialized.");

        _trees = Trees.instance(procenv);
        _types = Types.instance(((JavacProcessingEnvironment)procenv).getContext());
        _rootmaker = TreeMaker.instance(((JavacProcessingEnvironment)procenv).getContext());
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            JCCompilationUnit unit = toUnit(elem);
            System.out.println("Root elem " + elem);
            unit.accept(new GraphingVisitor());
        }
        return false;
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected class GraphingVisitor extends TreeScanner
    {
        public void scan (JCTree tree)
        {
            _indent += 2;
            super.scan(tree);
            _indent -= 2;
        }

        public void visitTopLevel (JCCompilationUnit tree) {
            println("Compilation unit: " + tree.sourcefile.getName());
            println("Package annotations");
            scan(tree.packageAnnotations);
            println("Package id");
            scan(tree.pid);
            println("Definitions");
            scan(tree.defs);
        }

        public void visitImport (JCImport tree) {
            println("Import " + tree.isStatic());
            super.visitImport(tree);
//             scan(tree.qualid);
        }

        public void visitClassDef (JCClassDecl tree) {
            println("Class '" + tree.getSimpleName() + "' " + what(tree.sym));
            super.visitClassDef(tree);
//             scan(tree.mods);
//             scan(tree.typarams);
//             scan(tree.extending);
//             scan(tree.implementing);
//             scan(tree.defs);
        }

        public void visitMethodDef (JCMethodDecl tree) {
            println("Method decl: " + tree.getName() + " " + what(tree.sym) +
                    " " + ASTUtil.isLibraryOverrider(_types, tree.sym));
            super.visitMethodDef(tree);
//             scan(tree.mods);
//             scan(tree.restype);
//             scan(tree.typarams);
//             scan(tree.params);
//             scan(tree.receiverAnnotations);
//             scan(tree.thrown);
//             scan(tree.defaultValue);
//             scan(tree.body);
        }

        public void visitVarDef (JCVariableDecl tree) {
            println("Var def: " + tree.getName() + " " + what(tree.sym));
            super.visitVarDef(tree);
//             scan(tree.mods);
//             scan(tree.vartype);
//             scan(tree.init);
        }

        public void visitSkip (JCSkip tree) {
            println("Skip");
            super.visitSkip(tree);
        }

        public void visitBlock (JCBlock tree) {
            println("Block");
            super.visitBlock(tree);
//             scan(tree.stats);
        }

        public void visitDoLoop (JCDoWhileLoop tree) {
            println("Do loop");
            super.visitDoLoop(tree);
//             scan(tree.body);
//             scan(tree.cond);
        }

        public void visitWhileLoop (JCWhileLoop tree) {
            println("While loop");
            super.visitWhileLoop(tree);
//             scan(tree.cond);
//             scan(tree.body);
        }

        public void visitForLoop (JCForLoop tree) {
            println("For loop");
            super.visitForLoop(tree);
//             scan(tree.init);
//             scan(tree.cond);
//             scan(tree.step);
//             scan(tree.body);
        }

        public void visitForeachLoop (JCEnhancedForLoop tree) {
            println("Foreach loop");
            super.visitForeachLoop(tree);
//             scan(tree.var);
//             scan(tree.expr);
//             scan(tree.body);
        }

        public void visitLabelled (JCLabeledStatement tree) {
            println("Labeled stmt");
            super.visitLabelled(tree);
//             scan(tree.body);
        }

        public void visitSwitch (JCSwitch tree) {
            println("Switch stmt");
            super.visitSwitch(tree);
//             scan(tree.selector);
//             scan(tree.cases);
        }

        public void visitCase (JCCase tree) {
            println("Case stmt");
            super.visitCase(tree);
//             scan(tree.pat);
//             scan(tree.stats);
        }

        public void visitSynchronized (JCSynchronized tree) {
            println("Sync block");
            super.visitSynchronized(tree);
//             scan(tree.lock);
//             scan(tree.body);
        }

        public void visitTry (JCTry tree) {
            println("Try block");
            super.visitTry(tree);
//             scan(tree.body);
//             scan(tree.catchers);
//             scan(tree.finalizer);
        }

        public void visitCatch (JCCatch tree) {
            println("Catch block");
            super.visitCatch(tree);
//             scan(tree.param);
//             scan(tree.body);
        }

        public void visitConditional (JCConditional tree) {
            println("Conditional");
            super.visitConditional(tree);
//             scan(tree.cond);
//             scan(tree.truepart);
//             scan(tree.falsepart);
        }

        public void visitIf (JCIf tree) {
            println("If stmt");
            super.visitIf(tree);
//             scan(tree.cond);
//             scan(tree.thenpart);
//             scan(tree.elsepart);
        }

        public void visitExec (JCExpressionStatement tree) {
            println("Expr stmt");
            super.visitExec(tree);
//             scan(tree.expr);
        }

        public void visitBreak (JCBreak tree) {
            println("Break stmt");
            super.visitBreak(tree);
        }

        public void visitContinue (JCContinue tree) {
            println("Cont stmt");
            super.visitContinue(tree);
        }

        public void visitReturn (JCReturn tree) {
            println("Return stmt");
            super.visitReturn(tree);
//             scan(tree.expr);
        }

        public void visitThrow (JCThrow tree) {
            println("Throw stmt");
            super.visitThrow(tree);
//             scan(tree.expr);
        }

        public void visitAssert (JCAssert tree) {
            println("Assert stmt");
            super.visitAssert(tree);
//             scan(tree.cond);
//             scan(tree.detail);
        }

        public void visitApply (JCMethodInvocation tree) {
            println("Call method");
            super.visitApply(tree);
//             scan(tree.meth);
//             scan(tree.args);
        }

        public void visitNewClass (JCNewClass tree) {
            println("New stmt");
            super.visitNewClass(tree);
//             scan(tree.encl);
//             scan(tree.clazz);
//             scan(tree.args);
//             scan(tree.def);
        }

        public void visitNewArray (JCNewArray tree) {
            println("New array stmt");
            super.visitNewArray(tree);
//             scan(tree.annotations);
//             scan(tree.elemtype);
//             scan(tree.dims);
//             for (List<JCTypeAnnotation> annos : tree.dimAnnotations)
//                 scan(annos);
//             scan(tree.elems);
        }

        public void visitParens (JCParens tree) {
            println("Paren expr");
            super.visitParens(tree);
//             scan(tree.expr);
        }

        public void visitAssign (JCAssign tree) {
            println("Assign stmt");
            super.visitAssign(tree);
//             scan(tree.lhs);
//             scan(tree.rhs);
        }

        public void visitAssignop (JCAssignOp tree) {
            println("Assignop expr: " + tree.getKind());
            super.visitAssignop(tree);
//             scan(tree.lhs);
//             scan(tree.rhs);
        }

        public void visitUnary (JCUnary tree) {
            println("Unary expr");
            super.visitUnary(tree);
//             scan(tree.arg);
        }

        public void visitBinary (JCBinary tree) {
            println("Binary expr: " + tree.getKind());
            super.visitBinary(tree);
//             scan(tree.lhs);
//             scan(tree.rhs);
        }

        public void visitTypeCast (JCTypeCast tree) {
            println("Type cast expr");
            super.visitTypeCast(tree);
//             scan(tree.clazz);
//             scan(tree.expr);
        }

        public void visitTypeTest (JCInstanceOf tree) {
            println("InstanceOf expr");
            super.visitTypeTest(tree);
//             scan(tree.expr);
//             scan(tree.clazz);
        }

        public void visitIndexed (JCArrayAccess tree) {
            println("Array access");
            super.visitIndexed(tree);
//             scan(tree.indexed);
//             scan(tree.index);
        }

        public void visitSelect (JCFieldAccess tree) {
            println("Field access: " + tree.getIdentifier());
            super.visitSelect(tree);
//             scan(tree.selected);
        }

        public void visitIdent (JCIdent tree) {
            println("Ident: " + tree.getName());
            super.visitIdent(tree);
        }

        public void visitLiteral (JCLiteral tree) {
            println("Literal: " + tree.value);
            super.visitLiteral(tree);
        }

        public void visitTypeIdent (JCPrimitiveTypeTree tree) {
            println("Type: " + tree.getKind() + " " + tree.getPrimitiveTypeKind());
            super.visitTypeIdent(tree);
        }

        public void visitTypeArray (JCArrayTypeTree tree) {
            println("Type array");
            super.visitTypeArray(tree);
//             scan(tree.elemtype);
        }

        public void visitTypeApply (JCTypeApply tree) {
            println("Type apply");
            super.visitTypeApply(tree);
//             scan(tree.clazz);
//             scan(tree.arguments);
        }

        public void visitTypeParameter (JCTypeParameter tree) {
            println("Type param");
            super.visitTypeParameter(tree);
//             scan(tree.annotations);
//             scan(tree.bounds);
        }

        public void visitWildcard (JCWildcard tree) {
            println("Type wildcard");
            super.visitWildcard(tree);
//             scan(tree.kind);
//             if (tree.inner != null)
//                 scan(tree.inner);
        }

//         public void visitTypeBoundKind (TypeBoundKind that) {
//             println("Type bound kind");
//             super.visitTypeBoundKind(that);
//         }

        public void visitModifiers (JCModifiers tree) {
            if (tree.getFlags().isEmpty() && tree.annotations.isEmpty()) {
                return; // print nothing if we have no modifiers or annotations
            }
            println("Modifiers: " + tree.getFlags());
            super.visitModifiers(tree);
//             scan(tree.annotations);
        }

        public void visitAnnotation (JCAnnotation tree) {
            println("Annotation");
            super.visitAnnotation(tree);
//             scan(tree.annotationType);
//             scan(tree.args);
        }

//         public void visitAnnotatedType (JCAnnotatedType tree) {
//             println("Annotated type");
//             super.visitAnnotatedType(tree);
// //             scan(tree.annotations);
// //             scan(tree.underlyingType);
//         }

        public void visitErroneous (JCErroneous tree) {
            println("Erroneousness");
            super.visitErroneous(tree);
        }

        public void visitLetExpr (LetExpr tree) {
            println("Let expr");
            super.visitLetExpr(tree);
//             scan(tree.defs);
//             scan(tree.expr);
        }

        protected void println (String text) {
            for (int ii = 0; ii < _indent; ii++) {
                System.out.print(" ");
            }
            System.out.println(text);
        }

        protected int _indent;
    }

    protected static String what (JCTree node)
    {
        if (node == null) {
            return "null";
        } else {
            return node.getClass().getSimpleName() + "[" + node + "]";
        }
    }

    protected static String what (Symbol sym)
    {
        return (sym == null) ? "nullsym" : ("(sym " + sym.type + "/" + what(sym.completer) + ")");
    }

    protected static String what (Object obj)
    {
        return (obj == null) ? "none" : obj.getClass().getSimpleName();
    }

    protected Trees _trees;
    protected Types _types;
    protected TreeMaker _rootmaker;
}
