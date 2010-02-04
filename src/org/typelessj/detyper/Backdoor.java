//
// $Id$

package org.typelessj.detyper;

import java.lang.reflect.Method;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

/**
 * Does some backdoor reflective invocation of useful javac internal methods.
 */
public class Backdoor
{
    /**
     * Initializes the backdoor, looking up methods that we will invoke reflectively.
     */
    public static boolean init (ProcessingEnvironment procenv)
    {
        if (_enterAnnotation == null) {
            _enterAnnotation = findMethod(procenv, Annotate.class, "enterAnnotation");
            _resolveMethod = findMethod(procenv, Resolve.class, "resolveMethod");
            _resolveConstructor = findMethod(procenv, Resolve.class, "resolveConstructor");
            _resolveQualifiedMethod = findMethod(procenv, Resolve.class, "resolveQualifiedMethod");
        }
        return (_enterAnnotation != null);
    }

    /**
     * Invokes {@link Annotate#enterAnnotation} reflectively.
     */
    public static Attribute.Compound enterAnnotation (
        Annotate annotate, JCAnnotation a, Type expected, Env<AttrContext> env)
    {
        try {
            return (Attribute.Compound)_enterAnnotation.invoke(annotate, a, expected, env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invokes {@link Resolve#resolveConstructor} reflectively.
     */
    public static Symbol resolveConstructor (
        Resolve resolve, JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env,
        Type site, List<Type> argtypes, List<Type> typeargtypes) {
        try {
            return (Symbol)_resolveConstructor.invoke(
                resolve, pos, env, site, argtypes, typeargtypes);
        } catch (Exception e) {
            throw new RuntimeException(unwrap(e));
        }
    }

    /**
     * Invokes {@link Resolve#resolveMethod} reflectively.
     */
    public static Symbol resolveMethod (
        Resolve resolve, JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env,
        Name name, List<Type> argtypes, List<Type> typeargtypes) {
        try {
            return (Symbol)_resolveMethod.invoke(resolve, pos, env, name, argtypes, typeargtypes);
        } catch (Exception e) {
            throw new RuntimeException(unwrap(e));
        }
    }

    /**
     * Invokes {@link Resolve#resolveQualifiedMethod} reflectively.
     */
    public static Symbol resolveQualifiedMethod (
        Resolve resolve, JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env,
        Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        try {
            return (Symbol)_resolveQualifiedMethod.invoke(
                resolve, pos, env, site, name, argtypes, typeargtypes);
        } catch (Exception e) {
            throw new RuntimeException(unwrap(e));
        }
    }

    protected static Method findMethod (ProcessingEnvironment procenv, Class<?> clazz, String name)
    {
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            procenv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unable to locate " +
                                               clazz.getSimpleName() + "." + name + " method.");
        } catch (Exception e) {
            e.printStackTrace();
            procenv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unable to access " +
                                               clazz.getSimpleName() + "." + name + " method.");
        }
        return null;
    }

    protected static Throwable unwrap (Throwable t)
    {
        return (t instanceof RuntimeException && t.getCause() != null) ? unwrap(t.getCause()) : t;
    }

    protected static Method _enterAnnotation;
    protected static Method _resolveMethod;
    protected static Method _resolveConstructor;
    protected static Method _resolveQualifiedMethod;
}
