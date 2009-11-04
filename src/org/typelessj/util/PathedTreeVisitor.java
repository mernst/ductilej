//
// $Id$

package org.typelessj.util;

import java.util.concurrent.Callable;

import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

/**
 * A tree translator that keeps track of the current path in the AST and allows queries to be
 * executed on that path. To avoid problematic interaction with overridden methods, one creates a
 * delegate visitor to do their processing and asks the containing pathed tree visitor about the
 * current path.
 */
public class PathedTreeVisitor extends JCTree.Visitor
{
    /** To use a TreeTranslator as a delegate to the PathedTreeVisitor you must extend this
     * customized version so that the necessary machinations are performed during tree traversal.
     * A more elegant solution is most welcome. */
    public static class TreeTranslatorDelegate extends TreeTranslator
        implements Delegate
    {
        // from interface PathedTreeVisitor.Delegate
        public void init (PathedTreeVisitor parent, PathedTreeVisitor.Path path) {
            _parent = parent;
            _path = path;
        }

        @Override public <T extends JCTree> T translate (T tree) {
            if (tree == null) {
                return null;
            } else {
                // we need to tell the node to accept our _parent, not this (the default behavior)
                tree.accept(_parent);
                @SuppressWarnings("unchecked") T result = (T)this.result;
                this.result = null;
                return result;
            }
        }

        protected PathedTreeVisitor _parent;
        protected Path _path;
    }

    /** Provides methods for current path to the AST root node. */
    public class Path {
        public List<JCTree> getPath () {
            return _path;
        }

        /** Returns true if the current path contains a node of the specified type. */
        public boolean contains (Class<? extends JCTree> nclass) {
            for (JCTree node : _path) {
                if (nclass.isInstance(node)) {
                    return true;
                }
            }
            return false;
        }

        @Override public String toString () {
            return Joiner.on(":").join(Iterables.transform(_path, new Function<JCTree,String>() {
                public String apply (JCTree node) {
                    return node.getClass().getSimpleName();
                }
            }));
        }

        void push (JCTree tree) {
            _path = _path.prepend(tree);
        }
        void pop () {
            _path = _path.tail;
        }

        protected List<JCTree> _path = List.nil();
    }

    public interface Delegate {
        public void init (PathedTreeVisitor parent, Path path);
    }

    public <T extends JCTree.Visitor & Delegate> PathedTreeVisitor (T delegate) {
        _delegate = delegate;
        delegate.init(this, _path);
    }

    public void visitTopLevel (JCCompilationUnit that) {
        _path.push(that);
        _delegate.visitTopLevel(that);
        _path.pop();
    }

    public void visitImport (JCImport that) {
        _path.push(that);
        _delegate.visitImport(that);
        _path.pop();
    }

    public void visitClassDef (JCClassDecl that) {
        _path.push(that);
        _delegate.visitClassDef(that);
        _path.pop();
    }

    public void visitMethodDef (JCMethodDecl that) {
        _path.push(that);
        _delegate.visitMethodDef(that);
        _path.pop();
    }

    public void visitVarDef (JCVariableDecl that) {
        _path.push(that);
        _delegate.visitVarDef(that);
        _path.pop();
    }

    public void visitSkip (JCSkip that) {
        _path.push(that);
        _delegate.visitSkip(that);
        _path.pop();
    }

    public void visitBlock (JCBlock that) {
        _path.push(that);
        _delegate.visitBlock(that);
        _path.pop();
    }

    public void visitDoLoop (JCDoWhileLoop that) {
        _path.push(that);
        _delegate.visitDoLoop(that);
        _path.pop();
    }

    public void visitWhileLoop (JCWhileLoop that) {
        _path.push(that);
        _delegate.visitWhileLoop(that);
        _path.pop();
    }

    public void visitForLoop (JCForLoop that) {
        _path.push(that);
        _delegate.visitForLoop(that);
        _path.pop();
    }

    public void visitForeachLoop (JCEnhancedForLoop that) {
        _path.push(that);
        _delegate.visitForeachLoop(that);
        _path.pop();
    }

    public void visitLabelled (JCLabeledStatement that) {
        _path.push(that);
        _delegate.visitLabelled(that);
        _path.pop();
    }

    public void visitSwitch (JCSwitch that) {
        _path.push(that);
        _delegate.visitSwitch(that);
        _path.pop();
    }

    public void visitCase (JCCase that) {
        _path.push(that);
        _delegate.visitCase(that);
        _path.pop();
    }

    public void visitSynchronized (JCSynchronized that) {
        _path.push(that);
        _delegate.visitSynchronized(that);
        _path.pop();
    }

    public void visitTry (JCTry that) {
        _path.push(that);
        _delegate.visitTry(that);
        _path.pop();
    }

    public void visitCatch (JCCatch that) {
        _path.push(that);
        _delegate.visitCatch(that);
        _path.pop();
    }

    public void visitConditional (JCConditional that) {
        _path.push(that);
        _delegate.visitConditional(that);
        _path.pop();
    }

    public void visitIf (JCIf that) {
        _path.push(that);
        _delegate.visitIf(that);
        _path.pop();
    }

    public void visitExec (JCExpressionStatement that) {
        _path.push(that);
        _delegate.visitExec(that);
        _path.pop();
    }

    public void visitBreak (JCBreak that) {
        _path.push(that);
        _delegate.visitBreak(that);
        _path.pop();
    }

    public void visitContinue (JCContinue that) {
        _path.push(that);
        _delegate.visitContinue(that);
        _path.pop();
    }

    public void visitReturn (JCReturn that) {
        _path.push(that);
        _delegate.visitReturn(that);
        _path.pop();
    }

    public void visitThrow (JCThrow that) {
        _path.push(that);
        _delegate.visitThrow(that);
        _path.pop();
    }

    public void visitAssert (JCAssert that) {
        _path.push(that);
        _delegate.visitAssert(that);
        _path.pop();
    }

    public void visitApply (JCMethodInvocation that) {
        _path.push(that);
        _delegate.visitApply(that);
        _path.pop();
    }

    public void visitNewClass (JCNewClass that) {
        _path.push(that);
        _delegate.visitNewClass(that);
        _path.pop();
    }

    public void visitNewArray (JCNewArray that) {
        _path.push(that);
        _delegate.visitNewArray(that);
        _path.pop();
    }

    public void visitParens (JCParens that) {
        _path.push(that);
        _delegate.visitParens(that);
        _path.pop();
    }

    public void visitAssign (JCAssign that) {
        _path.push(that);
        _delegate.visitAssign(that);
        _path.pop();
    }

    public void visitAssignop (JCAssignOp that) {
        _path.push(that);
        _delegate.visitAssignop(that);
        _path.pop();
    }

    public void visitUnary (JCUnary that) {
        _path.push(that);
        _delegate.visitUnary(that);
        _path.pop();
    }

    public void visitBinary (JCBinary that) {
        _path.push(that);
        _delegate.visitBinary(that);
        _path.pop();
    }

    public void visitTypeCast (JCTypeCast that) {
        _path.push(that);
        _delegate.visitTypeCast(that);
        _path.pop();
    }

    public void visitTypeTest (JCInstanceOf that) {
        _path.push(that);
        _delegate.visitTypeTest(that);
        _path.pop();
    }

    public void visitIndexed (JCArrayAccess that) {
        _path.push(that);
        _delegate.visitIndexed(that);
        _path.pop();
    }

    public void visitSelect (JCFieldAccess that) {
        _path.push(that);
        _delegate.visitSelect(that);
        _path.pop();
    }

    public void visitIdent (JCIdent that) {
        _path.push(that);
        _delegate.visitIdent(that);
        _path.pop();
    }

    public void visitLiteral (JCLiteral that) {
        _path.push(that);
        _delegate.visitLiteral(that);
        _path.pop();
    }

    public void visitTypeIdent (JCPrimitiveTypeTree that) {
        _path.push(that);
        _delegate.visitTypeIdent(that);
        _path.pop();
    }

    public void visitTypeArray (JCArrayTypeTree that) {
        _path.push(that);
        _delegate.visitTypeArray(that);
        _path.pop();
    }

    public void visitTypeApply (JCTypeApply that) {
        _path.push(that);
        _delegate.visitTypeApply(that);
        _path.pop();
    }

    public void visitTypeParameter (JCTypeParameter that) {
        _path.push(that);
        _delegate.visitTypeParameter(that);
        _path.pop();
    }

    public void visitWildcard (JCWildcard that) {
        _path.push(that);
        _delegate.visitWildcard(that);
        _path.pop();
    }

    public void visitTypeBoundKind (TypeBoundKind that) {
        _path.push(that);
        _delegate.visitTypeBoundKind(that);
        _path.pop();
    }

    public void visitAnnotation (JCAnnotation that) {
        _path.push(that);
        _delegate.visitAnnotation(that);
        _path.pop();
    }

    public void visitModifiers (JCModifiers that) {
        _path.push(that);
        _delegate.visitModifiers(that);
        _path.pop();
    }

    public void visitAnnotatedType (JCAnnotatedType that) {
        _path.push(that);
        _delegate.visitAnnotatedType(that);
        _path.pop();
    }

    public void visitErroneous (JCErroneous that) {
        _path.push(that);
        _delegate.visitErroneous(that);
        _path.pop();
    }

    public void visitLetExpr (LetExpr that) {
        _path.push(that);
        _delegate.visitLetExpr(that);
        _path.pop();
    }

    protected JCTree.Visitor _delegate;
    protected Path _path = new Path();
}
