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
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

/**
 * Does some backdoor reflective invocation of useful javac internal methods.
 */
public class Backdoor<T>
{
    public static final Backdoor<Attribute.Compound> enterAnnotation =
        newBackdoor(Annotate.class, "enterAnnotation", 3);
    public static final Backdoor<Type> classEnter =
        newBackdoor(Enter.class, "classEnter", 2);
    public static final Backdoor<Symbol> resolveMethod =
        newBackdoor(Resolve.class, "resolveMethod", 5);
    public static final Backdoor<Symbol> resolveConstructor =
        newBackdoor(Resolve.class, "resolveConstructor", 5);
    public static final Backdoor<Symbol> resolveQualifiedMethod =
        newBackdoor(Resolve.class, "resolveQualifiedMethod", 6);

    /**
     * Initializes the backdoor, looking up methods that we will invoke reflectively.
     */
    public static boolean init (ProcessingEnvironment procenv)
    {
        boolean haveErrors = !_errors.isEmpty();
        for (String error : _errors) {
            procenv.getMessager().printMessage(Diagnostic.Kind.WARNING, error);
        }
        _errors = List.nil();
        return haveErrors;
    }

    public T invoke (Object receiver, Object... args)
    {
        try {
            @SuppressWarnings("unchecked") T result = (T)_method.invoke(receiver, args);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(unwrap(e));
        }
    }

    protected Backdoor (Class<?> clazz, String name, int argCount)
    {
        String error = null;
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == argCount) {
                    m.setAccessible(true);
                    _method = m;
                    break;
                }
            }
            error = "Unable to locate " + clazz.getSimpleName() + "." + name + " method.";
        } catch (Exception e) {
            e.printStackTrace();
            error = "Unable to access " + clazz.getSimpleName() + "." + name + " method.";
        }
        if (error != null) {
            _errors = _errors.prepend(error);
        }
    }

    protected static <T> Backdoor<T> newBackdoor (Class<?> clazz, String name, int argCount)
    {
        return new Backdoor<T>(clazz, name, argCount);
    }

    protected static Throwable unwrap (Throwable t)
    {
        return (t instanceof RuntimeException && t.getCause() != null) ? unwrap(t.getCause()) : t;
    }

    protected Method _method;
    protected static List<String> _errors = List.nil();
}
