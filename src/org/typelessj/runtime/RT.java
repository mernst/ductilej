//
// $Id$

package org.typelessj.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Provides dynamic method dispatch, operator evaluation and other bits.
 */
public class RT
{
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
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            rargs = new Object[args.length+1];
            // our enclosing instance may be an instance of the inner class or an instance of the
            // enclosing class, in the former case, we need to extract the secret reference to the
            // enclosing class from the inner class and use that as our first argument
            rargs[0] = clazz.isInstance(encl) ? getEnclosingReference(encl) : encl;
            System.arraycopy(args, 0, rargs, 1, args.length);
        } else {
            rargs = args;
        }

        Constructor<?> ctor = findConstructor(clazz, rargs);
        if (ctor == null) {
            // TODO: if argument mismatch, clarify that
            throw new NoSuchMethodError(Debug.format("Can't find constructor for " +
                                                     clazz.getSimpleName(), "args", rargs));
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
            throw new RuntimeException(unwrap(ite.getCause()));
        }
    }

    /**
     * Creates a new empty array with the specified dimensions. TODO: we may need to box the array
     * and preserve the original type information.
     */
    public static Object boxArray (Class<?> etype, Object array)
    {
        return array;
    }

    /**
     * Creates a new empty array with the supplied initial arguments. TODO: we may need to box the
     * array and preserve the original type information.
     */
    public static Object boxArrayArgs (Class<?> etype, Object... elems)
    {
        // TODO: we may want to create an array of the supplied element type and copy the supplied
        // Object[] values thereinto
        return elems;
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
        return invoke(findMethod(mname, receiver.getClass(), args), receiver, args);
    }

    /**
     * Invokes the specified static method via reflection, performing runtime type resolution and
     * handling the necessary de-signature mangling.
     */
    public static Object invokeStatic (String mname, Class<?> clazz, Object... args)
    {
        return invoke(findMethod(mname, clazz, args), null, args);
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
            return findField(clazz, fname).get(target);
        } catch (NoSuchFieldException nsfe) {
            throw new RuntimeException(nsfe);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }

    /**
     * Assigns the specified value into the specified field of the target object.
     */
    public static Object assign (Object target, String fname, Object value)
    {
        if (target == null) {
            throw new NullPointerException("Field assignment to null target: " + fname);
        }

        try {
            Field field = findField(target.getClass(), fname);
            field.setAccessible(true);
            field.set(target, value);
            return value; // TODO: is the result of assignment the coerced type? in that case we
                          // need to return field.get(fname)

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
        if (opcode.equals("UNARY_MINUS")) {
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

        } else if (opcode.equals("BITWISE_COMPLEMENT")) {
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

        } else if ("LOGICAL_COMPLEMENT".equals(opcode)) {
            if (arg instanceof Boolean) {
                return !((Boolean)arg).booleanValue();
            } else {
                throw new IllegalArgumentException(
                    "Non-boolean type passed to logical complement.");
            }

        } else if ("UNARY_PLUS".equals(opcode)) {
            return arg;

// these are handled with inline tree transformations
//         case POSTFIX_INCREMENT
//         case POSTFIX_DECREMENT
//         case PREFIX_INCREMENT
//         case PREFIX_DECREMENT

        } else {
            throw new IllegalArgumentException("Unknown unary operator: " + opcode);
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
        // TODO: this all needs to be much more sophisticated
        if ("PLUS".equals(opcode)) {
            if (lhs instanceof String || rhs instanceof String) {
                return String.valueOf(lhs) + String.valueOf(rhs);
            }
            return OPS.get(promote((Number)lhs, (Number)rhs)).plus((Number)lhs, (Number)rhs);

        } else if ("MINUS".equals(opcode)) {
            return OPS.get(promote((Number)lhs, (Number)rhs)).minus((Number)lhs, (Number)rhs);

        } else if ("MULTIPLY".equals(opcode)) {
            return OPS.get(promote((Number)lhs, (Number)rhs)).multiply((Number)lhs, (Number)rhs);

        } else if ("DIVIDE".equals(opcode)) {
            return OPS.get(promote((Number)lhs, (Number)rhs)).divide((Number)lhs, (Number)rhs);

        } else if ("LESS_THAN".equals(opcode)) {
            return ((Number)lhs).doubleValue() < ((Number)rhs).doubleValue();

        } else if ("GREATER_THAN".equals(opcode)) {
            return ((Number)lhs).doubleValue() > ((Number)rhs).doubleValue();

        } else if ("LESS_THAN_EQUAL".equals(opcode)) {
            return ((Number)lhs).doubleValue() <= ((Number)rhs).doubleValue();

        } else if ("GREATER_THAN_EQUAL".equals(opcode)) {
            return ((Number)lhs).doubleValue() >= ((Number)rhs).doubleValue();

        } else if ("EQUAL_TO".equals(opcode)) {
            return isEqualTo(lhs, rhs);

        } else if ("NOT_EQUAL_TO".equals(opcode)) {
            return !isEqualTo(lhs, rhs);

        } else if ("CONDITIONAL_AND".equals(opcode)) {
            return !((Boolean)lhs).booleanValue() ? false : ((Boolean)rhs).booleanValue();

        } else if ("CONDITIONAL_OR".equals(opcode)) {
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
     * Converts the supplied value to a boolean. Supplying any non-Boolean instance will result in
     * a runtime failure.
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
     * Converts the supplied value to an int. If it is a boxed primitive type, it will first be
     * unboxed and then coerced to int which may result in loss of precision. Supplying any
     * non-numeric type will result in a runtime failure.
     */
    public static int asInt (Object value)
    {
        if (value instanceof Number) {
            return ((Number)value).intValue();
        } else {
            String type = (value == null) ? null : value.getClass().getSimpleName();
            throw new ClassCastException("Needed numeric type, got " + type);
        }
    }

    /**
     * A helper for {@link #select} and {@link #assign}.
     *
     * @throws NoSuchFieldException if the field could not be found.
     */
    protected static Field findField (Class<?> clazz, String fname)
        throws NoSuchFieldException
    {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(fname)) {
                return field;
            }
        }
        Class<?> parent = clazz.getSuperclass();
        if (parent == null) {
            throw new NoSuchFieldException(fname);
        }
        return findField(parent, fname);
    }

    /**
     * Invokes the specified method with the supplied arguments.
     */
    protected static Object invoke (Method method, Object receiver, Object... args)
    {
        // Debug.log("Invoking " + method, "recv", receiver, "args", args);

        // if this method is varargs we need to extract the variable arguments, place them into an
        // Object[] and create a new args array that has the varargs array in the final position
        Object[] aargs = args;
        if (method.isVarArgs()) {
            int fpcount = method.getParameterTypes().length-1;
            Object[] vargs = new Object[args.length-fpcount];
            System.arraycopy(args, fpcount, vargs, 0, args.length-fpcount);
            aargs = new Object[fpcount+1];
            System.arraycopy(args, 0, aargs, 0, fpcount);
            aargs[fpcount] = vargs;
        }

        try {
            method.setAccessible(true); // TODO: cache which methods we've toggled if slow
            return method.invoke(receiver, aargs);
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(unwrap(ite.getCause()));
        }
    }

    /**
     * A helper for {@link #invoke} and {@link #invokeStatic}.
     *
     * @throws NoSuchMethodError if a best matching method could not be found.
     */
    protected static Method findMethod (String mname, Class<?> clazz, Object[] args)
    {
        // TODO: this needs to follow the algorithm in JLS 15.12.2.1
        List<Method> methods = collectMethods(new ArrayList<Method>(), mname, clazz, args);

        // first look for matching non-varargs methods
        for (Method m : methods) {
            if (!m.isVarArgs()) {
                return m;
            }
        }

        // now look for any method
        if (methods.size() > 0) {
            return methods.get(0);
        }

        // TODO: if argument mismatch, clarify that, if total method lacking, clarify that
        throw new NoSuchMethodError("Can't find method " + clazz.getSimpleName() + "." + mname);
    }

    protected static List<Method> collectMethods (
        List<Method> into, String mname, Class<?> clazz, Object[] args)
    {
      METHODS:
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] ptypes = method.getParameterTypes();
            if (!method.getName().equals(mname)) {
                continue METHODS;
            }
            if (!(ptypes.length == args.length ||
                  (method.isVarArgs() && (ptypes.length-1) <= args.length))) {
                continue METHODS;
            }
            for (int ii = 0; ii < args.length; ii++) {
                Class<?> ptype = ptypes[ii].isPrimitive() ? WRAPPERS.get(ptypes[ii]) : ptypes[ii];
                if (args[ii] != null && !ptype.isAssignableFrom(args[ii].getClass())) {
                    continue METHODS;
                }
            }
            into.add(method);
        }
        Class<?> parent = clazz.getSuperclass();
        if (parent != null) {
            collectMethods(into, mname, parent, args);
        }
        return into;
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

    /**
     * Locates and returns the value of the secret reference to a non-static inner-class's
     * enclosing class.  We need this when we're constructing a non-static inner-class and have
     * only a reference to another instance of that non-static inner-class. In that case, the new
     * reference uses the same reference.
     */
    protected static Object getEnclosingReference (Object obj)
    {
        try {
            for (Field field : obj.getClass().getDeclaredFields()) {
                if (field.getName().equals("this$0")) {
                    field.setAccessible(true);
                    return field.get(obj);
                }
            }
            throw new RuntimeException("Failure finding enclosing reference");
        } catch (IllegalAccessException iae) {
            throw new RuntimeException("Failure accessing enclosing reference", iae);
        }
    }

    protected static Throwable unwrap (Throwable t)
    {
        return (t instanceof RuntimeException && t.getCause() != null) ? unwrap(t.getCause()) : t;
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
