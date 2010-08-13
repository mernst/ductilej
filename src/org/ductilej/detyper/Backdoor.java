//
// $Id$

package org.ductilej.detyper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree.*;

/**
 * Does some backdoor reflective invocation of useful javac internal methods.
 */
public class Backdoor<T>
{
    /** Provides type-safe access to reflected fields. */
    public interface FieldRef<A, V> {
        public V get (A arg);
        public void set (A arg, V value);
    }

    /** Provides type-safe access to reflected methods. */
    public interface MethodRef<R, V> {
        public V invoke (R receiver, Object... args);
    }

    public static final MethodRef<Annotate, Attribute.Compound> enterAnnotation =
        newMethodRef(Annotate.class, "enterAnnotation", 3);
    public static final MethodRef<Enter, Type> classEnter =
        newMethodRef(Enter.class, "classEnter", 2);
    public static final MethodRef<Resolve, Symbol> findIdent =
        newMethodRef(Resolve.class, "findIdent", 3);
    public static final MethodRef<Resolve, Symbol> findFun =
        newMethodRef(Resolve.class, "findFun", 6);
    public static final MethodRef<Resolve, Symbol> findMethod =
        newMethodRef(Resolve.class, "findMethod", 8);
    public static final MethodRef<Resolve, Symbol> resolveIdent =
        newMethodRef(Resolve.class, "resolveIdent", 4);
    public static final MethodRef<Resolve, Symbol> resolveMethod =
        newMethodRef(Resolve.class, "resolveMethod", 5);
    public static final MethodRef<Resolve, Symbol> resolveQualifiedMethod =
        newMethodRef(Resolve.class, "resolveQualifiedMethod", 6);
    public static final MethodRef<Resolve, Symbol> resolveConstructor =
        newMethodRef(Resolve.class, "resolveConstructor", 5);
    public static final MethodRef<Resolve, Type> instantiate =
        newMethodRef(Resolve.class, "instantiate", 8);
    public static final MethodRef<Attr, Symbol> selectSym =
        newMethodRef(Attr.class, "selectSym", 5);
    public static final MethodRef<MemberEnter, Type> signature =
        newMethodRef(MemberEnter.class, "signature", 5);

    public static final FieldRef<AttrContext, Scope> scope =
        newFieldRef(AttrContext.class, "scope");
    public static final FieldRef<AttrContext, Lint> lint =
        newFieldRef(AttrContext.class, "lint");
    public static final FieldRef<AttrContext, Boolean> selectSuper =
        newFieldRef(AttrContext.class, "selectSuper");
    public static final FieldRef<AttrContext, Boolean> varArgs =
        newFieldRef(AttrContext.class, "varArgs");

    protected static <A, V> FieldRef<A, V> newFieldRef (Class<A> clazz, String name)
    {
        try {
            for (final Field f : clazz.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    return new FieldRef<A, V>() {
                        public V get (A arg) {
                            try {
                                @SuppressWarnings("unchecked") V result = (V)f.get(arg);
                                return result;
                            } catch (Exception e) {
                                throw new RuntimeException(unwrap(e));
                            }
                        }
                        public void set (A arg, V value) {
                            try {
                                f.set(arg, value);
                            } catch (Exception e) {
                                throw new RuntimeException(unwrap(e));
                            }
                        }
                    };
                }
            }
            throw new RuntimeException("Unable to find '" + name + "' in " + clazz.getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static <R, V> MethodRef<R, V> newMethodRef (Class<R> clazz, String name, int argCount)
    {
        try {
            for (final Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == argCount) {
                    m.setAccessible(true);
                    return new MethodRef<R, V>() {
                        public V invoke (R receiver, Object... args) {
                            try {
                                @SuppressWarnings("unchecked") V result = (V)m.invoke(receiver, args);
                                return result;
                            } catch (Exception e) {
                                throw new RuntimeException(unwrap(e));
                            }
                        }
                        @Override public String toString () {
                            return m.getName();
                        }
                    };
                }
            }
            throw new RuntimeException(
                "Unable to locate " + clazz.getSimpleName() + "." + name + " method.");
        } catch (Exception e) {
            throw new RuntimeException(
                "Unable to access " + clazz.getSimpleName() + "." + name + " method.");
        }
    }

    protected static Throwable unwrap (Throwable t)
    {
        return (t instanceof RuntimeException && t.getCause() != null) ? unwrap(t.getCause()) : t;
    }

    protected Method _method;
}
