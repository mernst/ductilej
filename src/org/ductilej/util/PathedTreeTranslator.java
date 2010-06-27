//
// $Id$

package org.ductilej.util;

import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;

/**
 * Extends the {@link TreeTranslator} with path tracking and mechanisms to check whether or not we
 * are currently visiting a particular subtree (e.g. Apply.args or VarDef.init).
 *
 * Note: this class currently does not exhaustively handle sub-nodes. All AST nodes are represented
 * in the path but only some of those nodes have their edges exposed. For example, in Apply.args
 * args is not a node in the tree, it is an edge leading from Apply. Edges have been added ad hoc
 * where needed.
 */
public class PathedTreeTranslator extends TreeTranslator
{
    /**
     * Returns the current path as a string: <code>.TopLevel.ClassDef.MethodDef...</code> Every
     * node will be prefixed by a period to make it possible to do things like
     * <code>path().contains(".Foo.bar.Baz")</code>.
     */
    public String path ()
    {
        StringBuilder path = new StringBuilder();
        for (List<String> l = _path; !l.isEmpty(); l = l.tail) {
            path.insert(0, l.head);
            path.insert(0, ".");
        }
        return path.toString();
    }

    @Override public void visitTopLevel (JCCompilationUnit tree) {
        push("TopLevel");
        push("pid");
        tree.pid = translate(tree.pid);
        pop();
        tree.defs = translate(tree.defs);
        result = tree;
        pop();
    }

    @Override public void visitImport (JCImport tree) {
        push("Import");
        tree.qualid = translate(tree.qualid);
        result = tree;
        pop();
    }

    @Override public void visitClassDef (JCClassDecl tree) {
        push("ClassDef");
        tree.mods = translate(tree.mods);
        push("typarams");
        tree.typarams = translateTypeParams(tree.typarams);
        pop();
        push("extending");
        tree.extending = translate(tree.extending);
        pop();
        push("implementing");
        tree.implementing = translate(tree.implementing);
        pop();
        tree.defs = translate(tree.defs);
        result = tree;
        pop();
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
        push("MethodDef");
        tree.mods = translate(tree.mods);
        tree.restype = translate(tree.restype);
        tree.typarams = translateTypeParams(tree.typarams);
        push("params");
        tree.params = translateVarDefs(tree.params);
        pop();
        tree.thrown = translate(tree.thrown);
        push("body");
        tree.body = translate(tree.body);
        pop();
        result = tree;
        pop();
    }

    @Override public void visitVarDef (JCVariableDecl tree) {
        push("VarDef");
        tree.mods = translate(tree.mods);
        push("vartype");
        tree.vartype = translate(tree.vartype);
        pop();
        tree.init = translate(tree.init);
        result = tree;
        pop();
    }

    @Override public void visitSkip (JCSkip tree) {
        push("Skip");
        result = tree;
        pop();
    }

    @Override public void visitBlock (JCBlock tree) {
        push("Block");
        tree.stats = translate(tree.stats);
        result = tree;
        pop();
    }

    @Override public void visitDoLoop (JCDoWhileLoop tree) {
        push("DoLoop");
        tree.body = translate(tree.body);
        push("cond");
        tree.cond = translate(tree.cond);
        pop();
        result = tree;
        pop();
    }

    @Override public void visitWhileLoop (JCWhileLoop tree) {
        push("WhileLoop");
        push("cond");
        tree.cond = translate(tree.cond);
        pop();
        tree.body = translate(tree.body);
        result = tree;
        pop();
    }

    @Override public void visitForLoop (JCForLoop tree) {
        push("ForLoop");
        push("init");
        tree.init = translate(tree.init);
        pop();
        push("cond");
        tree.cond = translate(tree.cond);
        pop();
        push("step");
        tree.step = translate(tree.step);
        pop();
        tree.body = translate(tree.body);
        result = tree;
        pop();
    }

    @Override public void visitForeachLoop (JCEnhancedForLoop tree) {
        push("ForeachLoop");
        tree.var = translate(tree.var);
        push("expr");
        tree.expr = translate(tree.expr);
        pop();
        tree.body = translate(tree.body);
        result = tree;
        pop();
    }

    @Override public void visitLabelled (JCLabeledStatement tree) {
        push("Labelled");
        tree.body = translate(tree.body);
        result = tree;
        pop();
    }

    @Override public void visitSwitch (JCSwitch tree) {
        push("Switch");
        push("selector");
        tree.selector = translate(tree.selector);
        pop();
        tree.cases = translateCases(tree.cases);
        result = tree;
        pop();
    }

    @Override public void visitCase (JCCase tree) {
        push("Case");
        tree.pat = translate(tree.pat);
        tree.stats = translate(tree.stats);
        result = tree;
        pop();
    }

    @Override public void visitSynchronized (JCSynchronized tree) {
        push("Synchronized");
        push("lock");
        tree.lock = translate(tree.lock);
        pop();
        tree.body = translate(tree.body);
        result = tree;
        pop();
    }

    @Override public void visitTry (JCTry tree) {
        push("Try");
        tree.body = translate(tree.body);
        tree.catchers = translateCatchers(tree.catchers);
        tree.finalizer = translate(tree.finalizer);
        result = tree;
        pop();
    }

    @Override public void visitCatch (JCCatch tree) {
        push("Catch");
        tree.param = translate(tree.param);
        tree.body = translate(tree.body);
        result = tree;
        pop();
    }

    @Override public void visitConditional (JCConditional tree) {
        push("Conditional");
        push("cond");
        tree.cond = translate(tree.cond);
        pop();
        tree.truepart = translate(tree.truepart);
        tree.falsepart = translate(tree.falsepart);
        result = tree;
        pop();
    }

    @Override public void visitIf (JCIf tree) {
        push("If");
        push("cond");
        tree.cond = translate(tree.cond);
        pop();
        tree.thenpart = translate(tree.thenpart);
        tree.elsepart = translate(tree.elsepart);
        result = tree;
        pop();
    }

    @Override public void visitExec (JCExpressionStatement tree) {
        push("Exec");
        tree.expr = translate(tree.expr);
        result = tree;
        pop();
    }

    @Override public void visitBreak (JCBreak tree) {
        push("Break");
        result = tree;
        pop();
    }

    @Override public void visitContinue (JCContinue tree) {
        push("Continue");
        result = tree;
        pop();
    }

    @Override public void visitReturn (JCReturn tree) {
        push("Return");
        tree.expr = translate(tree.expr);
        result = tree;
        pop();
    }

    @Override public void visitThrow (JCThrow tree) {
        push("Throw");
        tree.expr = translate(tree.expr);
        result = tree;
        pop();
    }

    @Override public void visitAssert (JCAssert tree) {
        push("Assert");
        push("cond");
        tree.cond = translate(tree.cond);
        pop();
        tree.detail = translate(tree.detail);
        result = tree;
        pop();
    }

    @Override public void visitApply (JCMethodInvocation tree) {
        push("Apply");
        push("meth");
        tree.meth = translate(tree.meth);
        pop();
        push("args");
        tree.args = translate(tree.args);
        pop();
        result = tree;
        pop();
    }

    @Override public void visitNewClass (JCNewClass tree) {
        push("NewClass");
        tree.encl = translate(tree.encl);
        push("clazz");
        tree.clazz = translate(tree.clazz);
        pop();
        tree.args = translate(tree.args);
        tree.def = translate(tree.def);
        result = tree;
        pop();
    }

    @Override public void visitNewArray (JCNewArray tree) {
        push("NewArray");
        tree.annotations = translate(tree.annotations);
        List<List<JCTypeAnnotation>> dimAnnos = List.nil();
        for (List<JCTypeAnnotation> origDimAnnos : tree.dimAnnotations)
            dimAnnos = dimAnnos.append(translate(origDimAnnos));
        tree.dimAnnotations = dimAnnos;
        push("type");
        tree.elemtype = translate(tree.elemtype);
        pop();
        tree.dims = translate(tree.dims);
        tree.elems = translate(tree.elems);
        result = tree;
        pop();
    }

    @Override public void visitParens (JCParens tree) {
        push("Parens");
        tree.expr = translate(tree.expr);
        result = tree;
        pop();
    }

    @Override public void visitAssign (JCAssign tree) {
        push("Assign");
        push("lhs");
        tree.lhs = translate(tree.lhs);
        pop();
        push("rhs");
        tree.rhs = translate(tree.rhs);
        pop();
        result = tree;
        pop();
    }

    @Override public void visitAssignop (JCAssignOp tree) {
        push("Assignop");
        push("lhs");
        tree.lhs = translate(tree.lhs);
        pop();
        push("rhs");
        tree.rhs = translate(tree.rhs);
        pop();
        result = tree;
        pop();
    }

    @Override public void visitUnary (JCUnary tree) {
        push("Unary");
        tree.arg = translate(tree.arg);
        result = tree;
        pop();
    }

    @Override public void visitBinary (JCBinary tree) {
        push("Binary");
        tree.lhs = translate(tree.lhs);
        tree.rhs = translate(tree.rhs);
        result = tree;
        pop();
    }

    @Override public void visitTypeCast (JCTypeCast tree) {
        push("TypeCast");
        tree.clazz = translate(tree.clazz);
        tree.expr = translate(tree.expr);
        result = tree;
        pop();
    }

    @Override public void visitTypeTest (JCInstanceOf tree) {
        push("TypeTest");
        push("expr");
        tree.expr = translate(tree.expr);
        pop();
        tree.clazz = translate(tree.clazz);
        result = tree;
        pop();
    }

    @Override public void visitIndexed (JCArrayAccess tree) {
        push("Indexed");
        tree.indexed = translate(tree.indexed);
        tree.index = translate(tree.index);
        result = tree;
        pop();
    }

    @Override public void visitSelect (JCFieldAccess tree) {
        push("Select");
        tree.selected = translate(tree.selected);
        result = tree;
        pop();
    }

    @Override public void visitIdent (JCIdent tree) {
        push("Ident");
        result = tree;
        pop();
    }

    @Override public void visitLiteral (JCLiteral tree) {
        push("Literal");
        result = tree;
        pop();
    }

    @Override public void visitTypeIdent (JCPrimitiveTypeTree tree) {
        push("TypeIdent");
        result = tree;
        pop();
    }

    @Override public void visitTypeArray (JCArrayTypeTree tree) {
        push("TypeArray");
        tree.elemtype = translate(tree.elemtype);
        result = tree;
        pop();
    }

    @Override public void visitTypeApply (JCTypeApply tree) {
        push("TypeApply");
        tree.clazz = translate(tree.clazz);
        tree.arguments = translate(tree.arguments);
        result = tree;
        pop();
    }

    @Override public void visitTypeParameter (JCTypeParameter tree) {
        push("TypeParameter");
        tree.annotations = translate(tree.annotations);
        tree.bounds = translate(tree.bounds);
        result = tree;
        pop();
    }

    @Override public void visitWildcard (JCWildcard tree) {
        push("Wildcard");
        tree.kind = translate(tree.kind);
        tree.inner = translate(tree.inner);
        result = tree;
        pop();
    }

    @Override public void visitTypeBoundKind (TypeBoundKind tree) {
        push("TypeBoundKind");
        result = tree;
        pop();
    }

    @Override public void visitErroneous (JCErroneous tree) {
        push("Erroneous");
        result = tree;
        pop();
    }

    @Override public void visitLetExpr (LetExpr tree) {
        push("LetExpr");
        tree.defs = translateVarDefs(tree.defs);
        push("expr");
        tree.expr = translate(tree.expr);
        pop();
        result = tree;
        pop();
    }

    @Override public void visitModifiers (JCModifiers tree) {
        push("Modifiers");
        tree.annotations = translateAnnotations(tree.annotations);
        result = tree;
        pop();
    }

    @Override public void visitAnnotation (JCAnnotation tree) {
        push("Annotation");
        tree.annotationType = translate(tree.annotationType);
        tree.args = translate(tree.args);
        result = tree;
        pop();
    }

    @Override public void visitAnnotatedType (JCAnnotatedType tree) {
        push("AnnotatedType");
        tree.annotations = translate(tree.annotations);
        tree.underlyingType = translate(tree.underlyingType);
        result = tree;
        pop();
    }

    protected void push (String elem) {
        _path = _path.prepend(elem);
    }

    protected void pop () {
        _path = _path.tail;
    }

    protected List<String> _path = List.nil();
}
