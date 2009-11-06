//
// $Id$

package org.typelessj.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

// TODO: remove dependency on Tree.Kind from runtime
import com.sun.source.tree.Tree;

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
    public static <T> T newInstance (Class<T> clazz, Object encl, Object... args)
    {
        // if this is a non-static inner class, we need to shift a reference to the containing
        // class onto the constructor arguments
        Object[] rargs;
        if (!clazz.isMemberClass() || Modifier.isStatic(clazz.getModifiers())) {
            rargs = args;
        } else {
            rargs = new Object[args.length+1];
            rargs[0] = encl;
            System.arraycopy(args, 0, rargs, 1, args.length);
        }

        Constructor<?> ctor = findConstructor(clazz, rargs);
        if (ctor == null) {
            // TODO: if argument mismatch, clarify that
            throw new NoSuchMethodError("Can't find constructor for " + clazz.getSimpleName() +
                                        " matching args " + rargs);
        }

        try {
            ctor.setAccessible(true);
            // TODO: if this is a non-static inner class we need to shift the enclosing instance
            // into position zero of the arguments (I think)
            @SuppressWarnings("unchecked") T inst = (T)ctor.newInstance(rargs);
            return inst;
        } catch (InstantiationException ie) {
            throw new RuntimeException(ie);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite);
        }
    }

    /**
     * Creates a new empty array with the specified dimensions. TODO: we may need to box the array
     * and preserve the original type information.
     */
    public static Object boxArray (Class<?> clazz, Object array)
    {
        return array;
//         return Array.newInstance(Object.class, dims);
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
            // TODO: if argument mismatch, clarify that, if total method lacking, clarify that
            throw new NoSuchMethodError("Can't find method " +
                                        receiver.getClass().getSimpleName() + "." + mname);
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
            throw new NoSuchMethodError("Unable to find method " + mname +
                                        " (" + Arrays.asList(args) + ")"); // TODO
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
     * Returns the value of the field with the specified name in the specified target object. Also
     * handles the necessary magic to perform <code>someArray.length</code>.
     */
    public static Object select (String fname, Object target)
    {
        if (target == null) {
            throw new NullPointerException("Field access on null target");
        }

        Class<?> clazz = target.getClass();
        if (clazz.isArray()) {
            if (!fname.equals("length")) {
                throw new RuntimeException("Arrays have no fields other than 'length'");
            }
            return Array.getLength(target);
        }

        try {
            return clazz.getField(fname).get(target);
        } catch (NoSuchFieldException nsfe) {
            throw new RuntimeException(nsfe);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }

    /**
     * Assigns the specified value into the specified field of the target object.
     */
    public static Object assign (Object target, String mname, Object value)
    {
        if (target == null) {
            throw new NullPointerException("Field assignment to null target: " + mname);
        }

        try {
            Field field = target.getClass().getField(mname);
            field.setAccessible(true);
            field.set(target, value);
            return value; // TODO: is the result of assignment the coerced type? in that case we
                          // need to return field.get(mname)

        } catch (NoSuchFieldException nsfe) {
            throw new RuntimeException(nsfe);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
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
            return OPS.get(promote((Number)lhs, (Number)rhs)).plus((Number)lhs, (Number)rhs);

        case MINUS:
            return OPS.get(promote((Number)lhs, (Number)rhs)).minus((Number)lhs, (Number)rhs);

        case MULTIPLY:
            return OPS.get(promote((Number)lhs, (Number)rhs)).multiply((Number)lhs, (Number)rhs);

        case DIVIDE:
            return OPS.get(promote((Number)lhs, (Number)rhs)).divide((Number)lhs, (Number)rhs);

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

        case CONDITIONAL_AND:
            return !((Boolean)lhs).booleanValue() ? false : ((Boolean)rhs).booleanValue();

        case CONDITIONAL_OR:
            return ((Boolean)lhs).booleanValue() ? true : ((Boolean)rhs).booleanValue();
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
     * Performs assignment of the specified value into the specified array at the specified index.
     * Returns the assigned value.
     */
    public static Object assignAt (Object array, Object index, Object value)
    {
        Array.set(array, asInt(index), value);
        return value;
    }

    /**
     * Returns the element of the supplied array at the specified index.
     */
    public static Object atIndex (Object array, Object index)
    {
        return Array.get(array, ((Number)index).intValue());
    }

    /**
     * Casts the supplied value to the specified type. Triggers full context dump if the cast will
     * fail and we are forced to abort execution with a ClassCastException.
     */
    public static <T> T checkedCast (Class<T> clazz, Object value)
    {
        return clazz.cast(value); // TODO: catch CCE and trigger context dump
    }

    /**
     * Casts the supplied value to a boolean, failing if not possible. We could coerce integers to
     * booleans if we wanted to introduce extra sloppiness into TJ programming.
     */
    public static boolean asBoolean (Object value)
    {
        if (value instanceof Boolean) {
            return ((Boolean)value).booleanValue();
        } else {
            String type = (value == null) ? null : value.getClass().getSimpleName();
            throw new ClassCastException("Needed Boolean, got " + type);
        }
    }

    /**
     * "Casts" the supplied value to an int. If it is a boxed primitive type (either Java's boxes
     * or TJ's mutable boxes), it will first be unboxed and then cast to int which may result in
     * loss of precision. Supplying any non-numeric type will result in a runtime failure.
     */
    public static int asInt (Object value)
    {
        if (value instanceof Number) {
            return ((Number)value).intValue();
        } /* TODO: else if (value instanceof Boxed) {
        } */ else {
            String type = (value == null) ? null : value.getClass().getSimpleName();
            throw new ClassCastException("Needed numeric type, got " + type);
        }
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
    protected static Constructor<?> findConstructor (Class<?> clazz, Object... args)
    {
//         // enumerate all possible matching constructors
//         Class<?> target = clazz;
//         do {
//             List<Constructor<?>> ctors = Lists.newArrayList();
//             for (Constructor<?> ctor : target.getDeclaredConstructors()) {
//                 // TODO: make sure the argument types can match
//                 if (ptypes.length == args.length) {
//                     ctors.add(ctor);
//                 }
//             }
//             target = target.getSuperclass();
//         } while (target != null);
//         // TODO: sort them by best to worst match; return the first one

      CTORS:
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] ptypes = ctor.getParameterTypes();
            if (ptypes.length != args.length) {
                continue CTORS;
            }
            // debug("Checking " + ctor.getName() + " for match", "ptypes", ptypes, "args", args);
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

    /**
     * Returns the class to which to promote both sides of a numeric operation involving the
     * supplied left- and right-hand-sides.
     */
    protected static Class<?> promote (Number lhs, Number rhs)
    {
        // if either is a double, we promote to double
        Class<?> lhc = lhs.getClass(), rhc = rhs.getClass();
        if (lhc == Double.class || rhc == Double.class) {
            return Double.class;
        }

        // if one side is a float, then we promote to float unless the other side is a long in
        // which case we promote to double
        if (lhc == Float.class) {
            return promoteFloat(rhc);
        }
        if (rhc == Float.class) {
            return promoteFloat(lhc);
        }

        // otherwise we promote to the widest integer
        for (Class<?> clazz : PROMOTE_ORDER) {
            if (clazz == lhc || clazz == rhc) {
                return clazz;
            }
        }

        // we're not dealing with primitive types, so rightfully we should fail (we could do fun
        // things like support addition on BigDecimal and BigInteger and friends, but that's for
        // another day)
        throw new IllegalArgumentException("Unable to promote " + lhc + " and " + rhc);
    }

    protected static Class<?> promoteFloat (Class<?> other)
    {
        return (other == Long.class) ? Double.class : Float.class;
    }

    protected static interface MathOps {
        public Object plus (Number lhs, Number rhs);
        public Object minus (Number lhs, Number rhs);
        public Object multiply (Number lhs, Number rhs);
        public Object divide (Number lhs, Number rhs);
    };

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

    protected static final Class<?>[] PROMOTE_ORDER = {
        Long.class, Integer.class, Short.class, Byte.class };

    protected static final Map<Class<?>, MathOps> OPS =
        ImmutableMap.<Class<?>, MathOps>builder().
        put(Byte.class, new MathOps() {
            public Object plus (Number lhs, Number rhs) {
                return lhs.byteValue() + rhs.byteValue();
            }
            public Object minus (Number lhs, Number rhs) {
                return lhs.byteValue() - rhs.byteValue();
            }
            public Object multiply (Number lhs, Number rhs) {
                return lhs.byteValue() * rhs.byteValue();
            }
            public Object divide (Number lhs, Number rhs) {
                return lhs.byteValue() / rhs.byteValue();
            }
        }).
        put(Short.class, new MathOps() {
            public Object plus (Number lhs, Number rhs) {
                return lhs.shortValue() + rhs.shortValue();
            }
            public Object minus (Number lhs, Number rhs) {
                return lhs.shortValue() - rhs.shortValue();
            }
            public Object multiply (Number lhs, Number rhs) {
                return lhs.shortValue() * rhs.shortValue();
            }
            public Object divide (Number lhs, Number rhs) {
                return lhs.shortValue() / rhs.shortValue();
            }
        }).
        put(Integer.class, new MathOps() {
            public Object plus (Number lhs, Number rhs) {
                return lhs.intValue() + rhs.intValue();
            }
            public Object minus (Number lhs, Number rhs) {
                return lhs.intValue() - rhs.intValue();
            }
            public Object multiply (Number lhs, Number rhs) {
                return lhs.intValue() * rhs.intValue();
            }
            public Object divide (Number lhs, Number rhs) {
                return lhs.intValue() / rhs.intValue();
            }
        }).
        put(Long.class, new MathOps() {
            public Object plus (Number lhs, Number rhs) {
                return lhs.longValue() + rhs.longValue();
            }
            public Object minus (Number lhs, Number rhs) {
                return lhs.longValue() - rhs.longValue();
            }
            public Object multiply (Number lhs, Number rhs) {
                return lhs.longValue() * rhs.longValue();
            }
            public Object divide (Number lhs, Number rhs) {
                return lhs.longValue() / rhs.longValue();
            }
        }).
        put(Float.class, new MathOps() {
            public Object plus (Number lhs, Number rhs) {
                return lhs.floatValue() + rhs.floatValue();
            }
            public Object minus (Number lhs, Number rhs) {
                return lhs.floatValue() - rhs.floatValue();
            }
            public Object multiply (Number lhs, Number rhs) {
                return lhs.floatValue() * rhs.floatValue();
            }
            public Object divide (Number lhs, Number rhs) {
                return lhs.floatValue() / rhs.floatValue();
            }
        }).
        put(Double.class, new MathOps() {
            public Object plus (Number lhs, Number rhs) {
                return lhs.doubleValue() + rhs.doubleValue();
            }
            public Object minus (Number lhs, Number rhs) {
                return lhs.doubleValue() - rhs.doubleValue();
            }
            public Object multiply (Number lhs, Number rhs) {
                return lhs.doubleValue() * rhs.doubleValue();
            }
            public Object divide (Number lhs, Number rhs) {
                return lhs.doubleValue() / rhs.doubleValue();
            }
        }).
        build();
}
