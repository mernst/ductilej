//
// $Id$

package org.ductilej.util;

import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import org.ductilej.runtime.Debug;
import org.ductilej.runtime.Transformed;

/**
 * AST related utility methods.
 */
public class ASTUtil
{
    /**
     * Returns true if s is a method symbol that overrides a method in an untransformed superclass.
     * Adapted from javac's Check class.
     */
    public static boolean isLibraryOverrider (Types types, Symbol s)
    {
        if (s == null || s.kind != Kinds.MTH || s.isStatic()) {
            return false;
        }
        Symbol.MethodSymbol m = (Symbol.MethodSymbol)s;
        Symbol.TypeSymbol owner = (Symbol.TypeSymbol)m.owner;
        for (Type sup : types.closure(owner.type)) {
            if (sup == owner.type) {
                continue; // skip "this"
            }
            Scope scope = sup.tsym.members();
            for (Scope.Entry e = scope.lookup(m.name); e.scope != null; e = e.next()) {
                // if we override a library method, we're an overrider
                if (!e.sym.isStatic() && m.overrides(e.sym, owner, types, true) &&
                    isLibrary(e.sym)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the supplied symbol belongs to a class that has not been transformed (a
     * library class).
     */
    public static boolean isLibrary (Symbol sym)
    {
        if (sym == null) {
            Debug.log("Asked for isLibrary on null-symbol...");
            return false; // TODO: under what circumstances are we supplied with a null symbol?
        }

        if (!(sym instanceof Symbol.ClassSymbol)) {
            // TODO: this is assuming that if the containing class of an inner class is
            // transformed, then every inner class therein is transformed; this may or may not be
            // an acceptable assumption, I need to think about it
            return isLibrary(sym.outermostClass());
        }
        Symbol.ClassSymbol csym = (Symbol.ClassSymbol)sym;
        if (csym.fullname == null) { // we're an anonymous inner class
            return isLibrary(sym.outermostClass());
        }

        // if we're currently compiling this class from source, it's not a library; we need to make
        // this check because we may ask about a class that's involved in this compile and which
        // will be processed after the class that needs to know whether it's a library, so it will
        // not already have the @Transformed annotation; this allows us to avoid doing things in
        // two passes, where we first tag everything with @Transformed and then go through and
        // transform everything
        if (csym.classfile == null || csym.classfile.getKind() == JavaFileObject.Kind.SOURCE) {
            return false;
        }

        // check whether the class that defines this symbol has the @Transformed annotation
        for (List<Attribute.Compound> attrs = sym.attributes_field;
             !attrs.isEmpty(); attrs = attrs.tail) {
            if (Transformed.class.getName().equals(attrs.head.getAnnotationType().toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the supplied expression represents the void type (only shows up as the
     * declared return type for a method).
     */
    public static boolean isVoid (JCExpression expr)
    {
        return (expr instanceof JCPrimitiveTypeTree) &&
            ((JCPrimitiveTypeTree)expr).getPrimitiveTypeKind() == TypeKind.VOID;
    }

    /**
     * Returns true if the supplied modifiers include 'final', false otherwise.
     */
    public static boolean isFinal (JCModifiers mods)
    {
        return (mods.flags & Flags.FINAL) != 0;
    }

    /**
     * Returns true if the supplied symbol represents a varargs method.
     */
    public static boolean isVarArgs (long flags)
    {
        return (flags & Flags.VARARGS) != 0;
    }

    /**
     * Returns true if the supplied AST node represents the 'null' literal.
     */
    public static boolean isNullLiteral (JCTree expr)
    {
        return (expr.getTag() == JCTree.LITERAL) && (((JCLiteral)expr).typetag == TypeTags.BOT);
    }

    /**
     * Returns true if the supplied class declaration contains a static method declaration that
     * matches the supplied declaration in name and argument count. This does not descend into
     * inner class definitions, it only operates on the immediately supplied class.
     *
     * <p> Ideally we would also match argument type but we don't have that information at the time
     * we're doing this and all we're trying to figure out is which is the closest enclosing nested
     * class that defines a static method that is being called with no prefixed type. A collision
     * case is: <pre>
     * class Outer {
     *     static class Inner {
     *         static void foo (String arg) {}
     *         static void invoker () {
     *             foo(5); // will resolve to Inner.foo instead of Outer.foo
     *         }
     *     }
     *     static void foo (int arg) {}
     * }
     * </pre> but we're just punting on that for now.</p>
     */
    public static boolean definesStaticMethod (JCClassDecl ctree, JCMethodInvocation mtree)
    {
        final Name mname = TreeInfo.name(mtree.meth);
        final int argc = mtree.args.length();
        final boolean[] defines = new boolean[] { false };
        ctree.accept(new TreeScanner() {
            @Override public void visitClassDef (JCClassDecl tree) {
                if (!_inTopClass) { // we don't want to visiting inner class definitions
                    _inTopClass = true;
                    super.visitClassDef(tree);
                }
            }
            @Override public void visitMethodDef (JCMethodDecl tree) {
                if (tree.name == mname && tree.params.length() == argc) {
                    defines[0] = true;
                }
            }
            protected boolean _inTopClass;
        });
        return defines[0];
    }

    /**
     * Returns an expanded string representation of the supplied symbol. Used for debugging.
     */
    public static String expand (Symbol sym)
    {
        if (sym instanceof Symbol.ClassSymbol) {
            Symbol.ClassSymbol csym = (Symbol.ClassSymbol)sym;
            return csym.fullname + "(t=" + csym.type + ", " + csym.members_field + ")";
        } else {
            return String.valueOf(sym);
        }
    }

    public static String extype (Types types, Type type)
    {
        Type stype = types.supertype(type);
        return type /*+ "/" + type.tsym */ + "/" + isLibrary(type.tsym) +
            ((stype == null) ? "" : (" <- " + extype(types, stype)));
    }

    public static void dumpSyms (JCTree tree)
    {
        tree.accept(new TreeScanner() {
            public void visitClassDef (JCClassDecl tree) {
                log("Class", tree.name, tree.sym);
                _indent += 2;
                super.visitClassDef(tree);
                _indent -= 2;
            }
            public void visitMethodDef (JCMethodDecl tree) {
                log("Meth", tree.name, tree.sym);
                _indent += 2;
                super.visitMethodDef(tree);
                _indent -= 2;
            }
            public void visitVarDef (JCVariableDecl tree) {
                log("Var", tree.name, tree.sym);
                super.visitVarDef(tree);
            }
            protected void log (String what, Name name, Symbol sym) {
                StringBuilder buf = new StringBuilder();
                for (int ii = 0; ii < _indent; ii++) {
                    buf.append(" ");
                }
                buf.append(what).append(" ");
                buf.append(name).append(" ");
                buf.append(sym);
                Debug.temp(buf.toString());
            }
            protected int _indent = 0;
        });
    }
}
