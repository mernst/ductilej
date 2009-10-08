//
// $Id$

package org.typelessj.util;

import java.util.HashSet;
import java.util.Set;
import javax.lang.model.type.TypeKind;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import org.typelessj.runtime.Transformed;

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
     * Returns true if the supplied class declaration contains a static method declaration that
     * matches the supplied declaration in name and argument count. This does not descend into
     * inner class definitions, it only operates on the immediately supplied class.
     *
     * <p> Ideally we would also match argument type but we don't have that information at the time
     * we're doing this and all we're trying to figure out is which is the closest enclosing nested
     * class that defines a static method that is being called with no prefixed type. The collsion
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
     * </pre></p>
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
     * Enumerates all possible names of all classes visible in this compilation unit. This includes
     * all names visible in its import statements as well as the outer class in the compilation
     * unit, and any nested classes defined as well. By all possible, we mean that we include both
     * Inner and Outer.Inner in the set, we also include all variations of an import statement up
     * to its root because we don't want to make assumptions about which part of the import
     * statement refers to outer and inner classes and which refer to package components. Thus
     * 'import foo.bar.Baz.Bif' will result in Bif, Baz.Bif, bar.Baz.Bif and foo.bar.Baz.Bif' being
     * added as visible class names.
     *
     * <p> This is used later in heuristically determining whether a select expression refers to a
     * class or an object reference so that a determination can be made about whether we're looking
     * at a static or non-static method invocation. Because of the limited circumstances in which
     * these visible class names are used, we are only fooled by particular fairly unlikely (we
     * hope) constructions. </p>
     */
    public static Set<String> enumVisibleClassNames (JCCompilationUnit unit)
    {
        final Set<String> names = new HashSet<String>();
        unit.accept(new TreeScanner() {
            @Override public void visitImport (JCImport tree) {
                addImportNames(tree.qualid, List.<Name>nil());
            }
            @Override public void visitClassDef (JCClassDecl tree) {
                _path = _path.prepend(tree.name);
                for (List<Name> path = _path.reverse(); !path.isEmpty(); path = path.tail) {
                    names.add(path.toString("."));
                }
                super.visitClassDef(tree);
                _path = _path.tail;
            }
            protected void addImportNames (JCTree qualid, List<Name> path) {
                // TODO: we could stop once we see an uncapped component after having seen a capped
                // component (ie. stop at util in java.util.Collections.addAll)
                Name name;
                JCTree rest = null;
                if (qualid instanceof JCFieldAccess) {
                    name = ((JCFieldAccess)qualid).name;
                    rest = ((JCFieldAccess)qualid).selected;
                } else if (qualid instanceof JCIdent) {
                    name = ((JCIdent)qualid).name;
                } else { // not possible?
                    throw new RuntimeException(qualid + " is neither field access or ident");
                }
                path = path.prepend(name);
                names.add(path.toString("."));
                if (rest != null) {
                    addImportNames(rest, path);
                }
            }
            protected List<Name> _path = List.nil();
        });
        return names;
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
        return type + "/" + type.tsym + "/" + isLibrary(type.tsym) +
            ((stype == null) ? "" : (" <- " + extype(types, stype)));
    }
}
