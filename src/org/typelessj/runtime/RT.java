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
    /** A suffix appended to signature mangled method names. */
    public static final String MM_SUFFIX = "$M";

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
        boolean needsOuterThis = isInnerInNonStaticContext(clazz);
        boolean isMangled = (clazz.getAnnotation(Transformed.class) != null);
        Constructor<?> ctor = findConstructor(clazz, needsOuterThis, isMangled, args);
        if (ctor == null) {
            // TODO: if argument mismatch, clarify that
            throw new NoSuchMethodError(Debug.format("Can't find constructor for " +
                                                     clazz.getSimpleName(), "args", args));
        }

        // if this method is mangled, we need to add dummy arguments in the type-carrying parameter
        // positions
        Object[] rargs;
        if (isMangled) {
            List<Class<?>> ptypes = Arrays.asList(ctor.getParameterTypes());
            if (needsOuterThis) {
                ptypes = ptypes.subList(1, ptypes.size());
            }
            rargs = addMangleArgs(ptypes, args);
        } else {
            rargs = args;
        }

        // if this is an inner class in a non-static context, we need to shift a reference to the
        // containing class onto the constructor arguments
        if (needsOuterThis) {
            Object[] eargs = new Object[rargs.length+1];
            // our enclosing instance may be an instance of the inner class or an instance of the
            // enclosing class, in the former case, we need to extract the secret reference to the
            // enclosing class from the inner class and use that as our first argument
            eargs[0] = clazz.isInstance(encl) ? getEnclosingReference(encl) : encl;
            System.arraycopy(rargs, 0, eargs, 1, rargs.length);
            rargs = eargs;
        }

        try {
            ctor.setAccessible(true);
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
        try {
            // try to put the values into an array of the requested type
            Object tarray = Array.newInstance(etype, elems.length);
            for (int ii = 0, ll = elems.length; ii < ll; ii++) {
                Array.set(tarray, ii, elems[ii]);
            }
            return tarray;
        } catch (ArrayStoreException ase) {
            return elems; // fall back to an object array to contain the values
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
            Field field = findField(clazz, fname);
            field.setAccessible(true);
            return field.get(target);
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
        // TODO: this all needs to be much more sophisticated in the face of type errors
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

        } else if ("OR".equals(opcode)) {
            return ((Number)lhs).intValue() | ((Number)rhs).intValue();

        } else if ("AND".equals(opcode)) {
            return ((Number)lhs).intValue() & ((Number)rhs).intValue();

        } else if ("XOR".equals(opcode)) {
            return ((Number)lhs).intValue() ^ ((Number)rhs).intValue();

        } else if ("REMAINDER".equals(opcode)) {
            return ((Number)lhs).intValue() % ((Number)rhs).intValue();
        }

// TODO: implement
//         LEFT_SHIFT(BinaryTree.class),
//         RIGHT_SHIFT(BinaryTree.class),
//         UNSIGNED_RIGHT_SHIFT(BinaryTree.class),
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

        throw new IllegalArgumentException("Binop not yet implemented: " + opcode);
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
        Class<?> ptype = WRAPPERS.get(clazz);
        try {
            if (ptype == null) {
                return clazz.cast(value);
            } else {
                @SuppressWarnings("unchecked") T cvalue = (T)ptype.cast(value);
                return cvalue;
            }
        } catch (ClassCastException cce) {
            String vclass = (value == null) ? "<none>" : value.getClass().getName();
            Debug.log("Runtime cast failure", "target", clazz, "value", value, "vclass", vclass);
            // TODO: trigger context dump, terminate program?
            throw cce;
        }
    }

    /**
     * Casts the supplied value to the specified type. Triggers full context dump if the cast will
     * fail and we are forced to abort execution with a ClassCastException.
     *
     * <p>Note: we don't allow the type parameter of the supplied class to specify the return type
     * because we need to be able to supply the class literal for the upper bound of a type
     * variable but force the result type to be that of the type variable. For example given a type
     * variable <code>T</code> with upper bound <code>Object</code> we want to be able to do:
     * <code>T val = RT.<T>checkedCast(Object.class, oval)</code>
     */
    public static <T> T typeVarCast (Class<?> clazz, Object value)
    {
        try {
            // the upper bound of a type variable will never be a primitive type, so we don't need
            // to check WRAPPERS like we do in checkedCast()
            @SuppressWarnings("unchecked") T cvalue = (T)clazz.cast(value);
            return cvalue;
        } catch (ClassCastException cce) {
            String vclass = (value == null) ? "<none>" : value.getClass().getName();
            Debug.log("Runtime cast failure", "target", clazz, "value", value, "vclass", vclass);
            // TODO: trigger context dump, terminate program?
            throw cce;
        }
    }

    /**
     * Notes that the code contained a cast to the specified type. Does not fail with a
     * ClassCastException if the types do not match, rather notes the discrepancy and allows the
     * code to proceed.
     */
    public static Object noteCast (Class<?> clazz, Object value)
    {
        // casts of null are NOOPs
        if (value != null) {
            // TODO: check whether the value is of the specified type
        }
        return value;
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
        } else if (value instanceof Character) {
            return (int)(Character)value;
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
    protected static Object invoke (Method method, Object receiver, Object... rargs)
    {
        // Debug.log("Invoking " + method, "recv", receiver, "args", args);

        boolean isMangled = isMangled(method);
        List<Class<?>> ptypes = Arrays.asList(method.getParameterTypes());
        int pcount = ptypes.size();
        if (isMangled) {
            pcount /= 2;
        }

        // if this method is varargs we need to extract the variable arguments, place them into an
        // Object[] and create a new args array that has the varargs array in the final position
        Object[] aargs = rargs;
        if (method.isVarArgs()) {
            int fpcount = pcount-1, vacount = rargs.length-fpcount;
            // TEMP: we heuristically assume that if there's only one argument in the varargs
            // position and it's an array, then the caller did the wrapping already; the correct
            // thing to do is to use the static type of the single varargs argument and only skip
            // boxing if the static type is not an array type, but we don't yet reify static type
            // information into our runtime boxes
            if (vacount != 1 || (rargs[fpcount] != null && !rargs[fpcount].getClass().isArray())) {
                Object[] vargs = new Object[vacount];
                System.arraycopy(rargs, fpcount, vargs, 0, rargs.length-fpcount);
                aargs = new Object[fpcount+1];
                System.arraycopy(rargs, 0, aargs, 0, fpcount);
                aargs[fpcount] = vargs;
            }
        }

        // if this method is mangled, we need to add dummy arguments in the type-carrying parameter
        // positions
        if (isMangled) {
            aargs = addMangleArgs(ptypes, aargs);
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

        // no ambiguity, no problem!
        if (methods.size() == 1) {
            return methods.get(0);
        }

        // look for an exact type match (simplifies life for now)
      METHOD:
        for (Method m : methods) {
            Class<?>[] ptypes = m.getParameterTypes();
            int pcount = m.getName().endsWith("$M") ? ptypes.length/2 : ptypes.length;
            int poff = ptypes.length - pcount;
            for (int ii = 0, ll = Math.min(args.length, pcount); ii < ll; ii++) {
                Class<?> ptype = boxType(ptypes[poff+ii]);
                if (args[ii] != null && !ptype.equals(args[ii].getClass())) {
                    continue METHOD;
                }
            }
            return m;
        }

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
        throw new NoSuchMethodError(
            "Can't find method " + clazz + "." + mname + " (" + args.length + " args)");
    }

    /**
     * A helper for {@link #findMethod}.
     */
    protected static List<Method> collectMethods (
        List<Method> into, String mname, Class<?> clazz, Object[] args)
    {
        for (Method method : clazz.getDeclaredMethods()) {
            List<Class<?>> ptypes = Arrays.asList(method.getParameterTypes());
            String cmname = method.getName();
            boolean isMangled = isMangled(method);
            if (isMangled) {
                cmname = cmname.substring(0, cmname.length()-MM_SUFFIX.length());
            }
            if (cmname.equals(mname) && isApplicable(ptypes, isMangled, method.isVarArgs(), args)) {
                into.add(method);
            }
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
    protected static Constructor<?> findConstructor (Class<?> clazz, boolean needsOuterThis,
                                                     boolean isMangled, Object... args)
    {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            List<Class<?>> ptypes = Arrays.asList(ctor.getParameterTypes());
            if (needsOuterThis) {
                // ignore the outer this argument when testing for applicability
                ptypes = ptypes.subList(1, ptypes.size());
            }
            if (isApplicable(ptypes, isMangled, ctor.isVarArgs(), args)) {
                return ctor; // TODO: enumerate and select best match like findMethod()
            }
        }
        return null;
    }

    /**
     * Returns true if a method or constructor with the supplied arguments and variable arity can
     * be called with the supplied arguments.
     */
    protected static boolean isApplicable (
        List<Class<?>> ptypes, boolean isMangled, boolean isVarArgs, Object[] args)
    {
        int pcount = isMangled ? ptypes.size()/2 : ptypes.size();
        if (!(pcount == args.length || (isVarArgs && (pcount-1) <= args.length))) {
            return false;
        }

        // make sure all fixed arity arguments match
        int fpcount = isVarArgs ? pcount-1 : pcount, poff = isMangled ? pcount : 0;
        for (int ii = 0; ii < fpcount; ii++) {
            Class<?> ptype = boxType(ptypes.get(poff + ii));
            if (args[ii] != null && !ptype.isAssignableFrom(args[ii].getClass())) {
                return false;
            }
        }

// TODO: should we leave this out, or is there some better check we can do given that detyped
// varargs becomes foo(Object vargs) and library varargs is foo(T[] vargs); maybe only enforce type
// if we see an actual array type (since we then know we have to cast)

//         // make sure all variable artity arguments match
//         if (isVarArgs) {
//             Class<?> ptype = boxType(ptypes[poff+fpcount]);
//             for (int ii = fpcount; ii < args.length; ii++) {
//                 if (args[ii] != null && !ptype.isAssignableFrom(args[ii].getClass())) {
//                     return false;
//                 }
//             }
//         }
        return true;
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

    protected static boolean isMangled (Method method)
    {
        return method.getName().endsWith(MM_SUFFIX);
    }

    protected static Object[] addMangleArgs (List<Class<?>> ptypes, Object[] args)
    {
        Object[] margs = new Object[args.length*2];
        System.arraycopy(args, 0, margs, 0, args.length);
        for (int ii = args.length; ii < ptypes.size(); ii++) {
            // if the argument is a primitive type, DUMMIES will contain a dummy value for that
            // type, otherwise it will return null which is the desired dummy value for all
            // non-primitive types
            margs[ii] = DUMMIES.get(ptypes.get(ii));
        }
        return margs;
    }

    protected static boolean isInnerInNonStaticContext (Class<?> clazz)
    {
        if (clazz.isLocalClass() || clazz.isAnonymousClass()) {
            // a local or anonymous class must be declared in a method or a constructor, in the
            // latter case, we are guaranteed to be in a non-static context
            Method em = clazz.getEnclosingMethod();
            return (em == null) || !Modifier.isStatic(em.getModifiers());
        } else {
            return clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers());
        }
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

    protected static Class<?> boxType (Class<?> type)
    {
        return type.isPrimitive() ? WRAPPERS.get(type) : type;
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

    protected static final Map<Class<?>, Object> DUMMIES =
        ImmutableMap.<Class<?>, Object>builder().
        put(Boolean.TYPE, false).
        put(Byte.TYPE, (byte)0).
        put(Character.TYPE, (char)0).
        put(Short.TYPE, (short)0).
        put(Integer.TYPE, 0).
        put(Long.TYPE, 0l).
        put(Float.TYPE, 0f).
        put(Double.TYPE, 0d).
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
