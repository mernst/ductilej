//
// $Id$

package org.typelessj.detyper;

import java.lang.reflect.Method;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree.*;

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
        }
        return (_enterAnnotation != null);
    }

    /**
     * Invokes {@link Annotate.enterAnnotation} reflectively.
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

    protected static Method _enterAnnotation;
}
