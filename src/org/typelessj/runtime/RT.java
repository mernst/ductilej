//
// $Id$

package org.typelessj.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;

import com.google.common.collect.ImmutableMap;
import com.samskivert.util.LogBuilder;

/**
 * Provides dynamic method dispatch, operator evaluation and other bits.
 */
public class RT
{
    /**
     * Emits a debug message.
     *
     * @param args key/value pairs, (e.g. "age", someAge, "size", someSize) which will be appended
     * to the log message as [age=someAge, size=someSize].
     */
    public static void debug (String message, Object... args)
    {
        System.out.println(new LogBuilder(message, args));
    }

    /**
     * Invokes the constructor of the supplied class, with the specified arguments and returns the
     * newly created instance.
     *
     * @param clazz the class to be instantiated.
     * @param encl the enclosing instance to use in the case of a non-static inner class.
     * @param args the arguments to be supplied to the constructor, if any.
     */
    public static Object newInstance (Class<?> clazz, Object encl, Object... args)
    {
        Constructor ctor = findConstructor(clazz, args);
        if (ctor == null) {
            throw new NoSuchMethodError(); // TODO
        }

        try {
            ctor.setAccessible(true);
            // TODO: if this is a non-static inner class we need to shift the enclosing instance
            // into position zero of the arguments (I think)
            return ctor.newInstance(args);
        } catch (InstantiationException ie) {
            throw new RuntimeException(ie);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite);
        }
    }

    /**
     * Invokes the specified method via reflection, performing runtime type resolution and handling
     * the necessary signature de-mangling.
     */
    public static Object invoke (String mname, Object receiver, Object... args)
    {
        if (receiver == null) {
            throw new NullPointerException();
        }

        Method method = findMethod(mname, receiver.getClass(), args);
        if (method == null) {
            throw new NoSuchMethodError(); // TODO
        }

        try {
            method.setAccessible(true); // TODO: cache which methods we've toggled if slow
            return method.invoke(receiver, args);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite);
        }
    }

    /**
     * Invokes the specified static method via reflection, performing runtime type resolution and
     * handling the necessary de-signature mangling.
     */
    public static Object invokeStatic (String mname, Class<?> clazz, Object... args)
    {
        Method method = findMethod(mname, clazz, args);
        if (method == null) {
            throw new NoSuchMethodError(); // TODO
        }

        try {
            method.setAccessible(true); // TODO: cache which methods we've toggled if slow
            return method.invoke(null, args);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite);
        }
    }

    /**
     * Executes the specified comparison operation on the supplied left- and right-hand-sides.
     *
     * @param opcode the string value of com.sun.source.tree.Tree.Kind for the comparison operator
     * in question.
     */
    public static boolean compare (String opcode, Object lhs, Object rhs)
    {

        return false;
    }

    /**
     * Executes the specified operation on the supplied argument.
     *
     * @param opcode the string value of com.sun.source.tree.Tree.Kind for the operator in
     * question.
     */
    public static Object unop (String opcode, Object arg)
    {
        Tree.Kind kind = Enum.valueOf(Tree.Kind.class, opcode);
        switch (kind) {
        case UNARY_MINUS:
            if (arg instanceof Byte) {
                return -((Byte)arg).byteValue();
            } else if (arg instanceof Short) {
                return -((Short)arg).shortValue();
            } else if (arg instanceof Integer) {
                return -((Integer)arg).intValue();
            } else if (arg instanceof Long) {
                return -((Long)arg).longValue();
            } else if (arg instanceof Float) {
                return -((Float)arg).floatValue();
            } else if (arg instanceof Double) {
                return -((Double)arg).doubleValue();
            } else {
                throw new IllegalArgumentException("Non-numeric type passed to unary minus.");
            }

        case BITWISE_COMPLEMENT:
            if (arg instanceof Byte) {
                return ~((Byte)arg).byteValue();
            } else if (arg instanceof Short) {
                return ~((Short)arg).shortValue();
            } else if (arg instanceof Integer) {
                return ~((Integer)arg).intValue();
            } else if (arg instanceof Long) {
                return ~((Long)arg).longValue();
            } else if (arg instanceof Float) {
                return -((Float)arg).floatValue();
            } else if (arg instanceof Double) {
                return -((Double)arg).doubleValue();
            } else {
                throw new IllegalArgumentException(
                    "Non-numeric type passed to bitwise complement.");
            }

        case LOGICAL_COMPLEMENT:
            if (arg instanceof Boolean) {
                return !((Boolean)arg).booleanValue();
            } else {
                throw new IllegalArgumentException(
                    "Non-boolean type passed to logical complement.");
            }

        case UNARY_PLUS:
            return arg;

// these must be handled with inline code generation
//         case POSTFIX_INCREMENT:
//             return null; // TODO
//         case POSTFIX_DECREMENT:
//             return null; // TODO
//         case PREFIX_INCREMENT:
//             return null; // TODO
//         case PREFIX_DECREMENT:
//             return null; // TODO

        default:
            throw new IllegalArgumentException("Unknown unary operator: " + kind);
        }
    }

    /**
     * Executes the specified operation on the supplied left- and right-hand-sides.
     *
     * @param opcode the string value of com.sun.source.tree.Tree.Kind for the operator in
     * question.
     */
    public static Object binop (String opcode, Object lhs, Object rhs)
    {
        Tree.Kind kind = Enum.valueOf(Tree.Kind.class, opcode);
        // TODO: this all needs to be much more sophisticated
        switch (kind) {
        case PLUS:
            if (lhs instanceof String || rhs instanceof String) {
                return String.valueOf(lhs) + String.valueOf(rhs);
            }
            return ((Integer)lhs).intValue() + ((Integer)rhs).intValue();

        case MULTIPLY:
            return ((Integer)lhs).intValue() * ((Integer)rhs).intValue();

        case LESS_THAN:
            return ((Number)lhs).doubleValue() < ((Number)rhs).doubleValue();

        case GREATER_THAN:
            return ((Number)lhs).doubleValue() > ((Number)rhs).doubleValue();

        case LESS_THAN_EQUAL:
            return ((Number)lhs).doubleValue() <= ((Number)rhs).doubleValue();

        case GREATER_THAN_EQUAL:
            return ((Number)lhs).doubleValue() >= ((Number)rhs).doubleValue();

        case EQUAL_TO:
            return isEqualTo(lhs, rhs);

        case NOT_EQUAL_TO:
            return !isEqualTo(lhs, rhs);

//         CONDITIONAL_AND(BinaryTree.class),
//         CONDITIONAL_OR(BinaryTree.class),
        }

// TODO: implement
//         DIVIDE(BinaryTree.class),
//         REMAINDER(BinaryTree.class),
//         MINUS(BinaryTree.class),
//         LEFT_SHIFT(BinaryTree.class),
//         RIGHT_SHIFT(BinaryTree.class),
//         UNSIGNED_RIGHT_SHIFT(BinaryTree.class),
//         AND(BinaryTree.class),
//         XOR(BinaryTree.class),
//         OR(BinaryTree.class),
//         MULTIPLY_ASSIGNMENT(CompoundAssignmentTree.class),
//         DIVIDE_ASSIGNMENT(CompoundAssignmentTree.class),
//         REMAINDER_ASSIGNMENT(CompoundAssignmentTree.class),
//         PLUS_ASSIGNMENT(CompoundAssignmentTree.class),
//         MINUS_ASSIGNMENT(CompoundAssignmentTree.class),
//         LEFT_SHIFT_ASSIGNMENT(CompoundAssignmentTree.class),
//         RIGHT_SHIFT_ASSIGNMENT(CompoundAssignmentTree.class),
//         UNSIGNED_RIGHT_SHIFT_ASSIGNMENT(CompoundAssignmentTree.class),
//         AND_ASSIGNMENT(CompoundAssignmentTree.class),
//         XOR_ASSIGNMENT(CompoundAssignmentTree.class),
//         OR_ASSIGNMENT(CompoundAssignmentTree.class),

        return null;
    }

    /**
     * Casts an object to an iterable over objects. Used to massage foreach expressions.
     */
    public static Iterable<Object> asIterable (Object arg)
    {
        if (arg instanceof Object[]) {
            return Arrays.asList((Object[])arg);
        } else {
            @SuppressWarnings("unchecked") Iterable<Object> casted = (Iterable<Object>)arg;
            return casted;
        }
    }

    /**
     * Returns the element of the supplied array at the specified index.
     */
    public static Object atIndex (Object array, Object index)
    {
        return Array.get(array, ((Number)index).intValue());
    }

    /**
     * A helper for {@link #invoke} and {@link #invokeStatic}.
     */
    protected static Method findMethod (String mname, Class<?> clazz, Object... args)
    {
        // TODO: this needs to be much smarter :)
      METHODS:
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] ptypes = method.getParameterTypes();
            if (!method.getName().equals(mname) || ptypes.length != args.length) {
                continue METHODS;
            }
            // debug("Checking " + method.getName() + " for match", "ptypes", ptypes, "args", args);
            for (int ii = 0; ii < args.length; ii++) {
                Class<?> ptype = ptypes[ii].isPrimitive() ? WRAPPERS.get(ptypes[ii]) : ptypes[ii];
                if (args[ii] != null && !ptype.isAssignableFrom(args[ii].getClass())) {
                    continue METHODS;
                }
            }
            return method;
        }
        Class<?> parent = clazz.getSuperclass();
        return (parent == null) ? null : findMethod(mname, parent, args);
    }

    /**
     * A helper for {@link #newInstance}.
     */
    protected static Constructor findConstructor (Class<?> clazz, Object... args)
    {
        // TODO: this needs to be smarter :)
      CTORS:
        for (Constructor ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] ptypes = ctor.getParameterTypes();
            if (ptypes.length != args.length) {
                continue CTORS;
            }
            // debug("Checking " + method.getName() + " for match", "ptypes", ptypes, "args", args);
            for (int ii = 0; ii < args.length; ii++) {
                Class<?> ptype = ptypes[ii].isPrimitive() ? WRAPPERS.get(ptypes[ii]) : ptypes[ii];
                if (args[ii] != null && !ptype.isAssignableFrom(args[ii].getClass())) {
                    continue CTORS;
                }
            }
            return ctor;
        }
        Class<?> parent = clazz.getSuperclass();
        return (parent == null) ? null : findConstructor(parent, args);
    }

    protected static boolean isEqualTo (Object lhs, Object rhs)
    {
        // TODO: if the original code had Integer == Integer it would have done reference
        // equality, but we don't (can't?) differentiate between that and two Integer objects
        // that we promoted from ints and so we always do equals() equality on them as long as
        // both sides are non-null
        if (lhs instanceof Number && rhs instanceof Number) {
            // TODO: handle promotion because Integer.equals(Byte) does not work
            return lhs.equals(rhs);
        } else {
            return lhs == rhs;
        }
    }

    protected static final Map<Class<?>, Class<?>> WRAPPERS =
        ImmutableMap.<Class<?>, Class<?>>builder().
        put(Boolean.TYPE, Boolean.class).
        put(Byte.TYPE, Byte.class).
        put(Character.TYPE, Character.class).
        put(Short.TYPE, Short.class).
        put(Integer.TYPE, Integer.class).
        put(Long.TYPE, Long.class).
        put(Float.TYPE, Float.class).
        put(Double.TYPE, Double.class).
        build();
}
