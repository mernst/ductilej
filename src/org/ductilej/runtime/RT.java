//
// $Id$

package org.ductilej.runtime;

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

import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

import org.ductilej.runtime.ops.OpsUtil;

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
            eargs[0] = clazz.isInstance(encl) ?
                getEnclosingReference(clazz.getEnclosingClass(), encl) : encl;
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
            throw new NullPointerException(
                Debug.format("Null receiver for " + mname, "atypes", toArgTypes(args)));
        }
        Class<?> orclass = receiver.getClass();
        Class<?> rclass = orclass;
        Class<?>[] atypes = toArgTypes(args);
        Method m;
        do {
            m = findMethod(mname, rclass, atypes);
            if (m == null) {
                rclass = rclass.getEnclosingClass();
                if (rclass != null) {
                    receiver = getEnclosingReference(rclass, receiver);
                }
            }
        } while (m == null && rclass != null);
        return invoke(checkMethod(m, mname, orclass, args), receiver, args);
    }

    /**
     * Invokes the specified static method via reflection, performing runtime type resolution and
     * handling the necessary de-signature mangling.
     */
    public static Object invokeStatic (String mname, Class<?> clazz, Object... args)
    {
        Method m = findMethod(mname, clazz, toArgTypes(args));
        return invoke(checkMethod(m, mname, clazz, args), null, args);
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
        if ("UNARY_MINUS".equals(opcode)) {
            return OpsUtil.get(arg).minus(arg);
        } else if ("UNARY_PLUS".equals(opcode)) {
            return OpsUtil.get(arg).plus(arg);

        } else if ("BITWISE_COMPLEMENT".equals(opcode)) {
            return OpsUtil.get(arg).bitComp(arg);
        } else if ("LOGICAL_COMPLEMENT".equals(opcode)) {
            return OpsUtil.get(arg).logicalComp(arg);

        // the side effects for these operations are handled by rewriting the AST, so they simply
        // need to return an incremented or decremented value
        } else if ("PREFIX_INCREMENT".equals(opcode)) {
            return OpsUtil.get(arg).increment(arg);
        } else if ("POSTFIX_INCREMENT".equals(opcode)) {
            return OpsUtil.get(arg).increment(arg);
        } else if ("PREFIX_DECREMENT".equals(opcode)) {
            return OpsUtil.get(arg).decrement(arg);
        } else if ("POSTFIX_DECREMENT".equals(opcode)) {
            return OpsUtil.get(arg).decrement(arg);
        }

        throw new IllegalArgumentException("Unsupported unary operator: " + opcode);
    }

    /**
     * Executes the specified operation on the supplied left- and right-hand-sides.
     *
     * @param opcode the string value of com.sun.source.tree.Tree.Kind for the operator in
     * question.
     */
    public static Object binop (String opcode, Object lhs, Object rhs)
    {
        if ("PLUS".equals(opcode)) {
            if (lhs instanceof String || rhs instanceof String) {
                return String.valueOf(lhs) + String.valueOf(rhs);
            }
            return OpsUtil.get(lhs, rhs).plus(lhs, rhs);

        } else if ("MINUS".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).minus(lhs, rhs);
        } else if ("MULTIPLY".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).multiply(lhs, rhs);
        } else if ("DIVIDE".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).divide(lhs, rhs);
        } else if ("REMAINDER".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).remainder(lhs, rhs);

        } else if ("LESS_THAN".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).lessThan(lhs, rhs);
        } else if ("GREATER_THAN".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).greaterThan(lhs, rhs);
        } else if ("LESS_THAN_EQUAL".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).lessThanEq(lhs, rhs);
        } else if ("GREATER_THAN_EQUAL".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).greaterThanEq(lhs, rhs);

        } else if ("EQUAL_TO".equals(opcode)) {
            return OpsUtil.isEqualTo(lhs, rhs);
        } else if ("NOT_EQUAL_TO".equals(opcode)) {
            return !OpsUtil.isEqualTo(lhs, rhs);

        } else if ("OR".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).bitOr(lhs, rhs);
        } else if ("AND".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).bitAnd(lhs, rhs);
        } else if ("XOR".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).bitXor(lhs, rhs);

        } else if ("LEFT_SHIFT".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).leftShift(lhs, rhs);
        } else if ("RIGHT_SHIFT".equals(opcode)) {
            return OpsUtil.get(lhs, rhs).rightShift(lhs, rhs);
// TODO: implement
//         UNSIGNED_RIGHT_SHIFT(BinaryTree.class),

        } else if ("CONDITIONAL_AND".equals(opcode)) {
            throw new IllegalArgumentException("&& should not be lifted");
        } else if ("CONDITIONAL_OR".equals(opcode)) {
            throw new IllegalArgumentException("|| should not be lifted");
        }

// the assignment operators are transformed into non-assignment versions by the detyper
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

        throw new IllegalArgumentException("Unsupported binary op: " + opcode);
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
     * Performs primitive numeric coercion on a value.
     */
    public static Object coerce (Class<?> clazz, Object value)
    {
        if (value == null) {
            throw new NullPointerException("Cannot coerce null to " + clazz.getName());
        }
        Coercer c = COERCERS.get(Tuple.create(value.getClass(), clazz));
        if (c == null) {
            throw new IllegalArgumentException(
                "Cannot coerce " + value.getClass().getName() + " to " + clazz.getName());
        }
        return c.coerce(value);
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
        // Debug.temp("Invoking " + method, "recv", receiver, "args", rargs);

        boolean isMangled = isMangled(method);
        List<Class<?>> ptypes = Arrays.asList(method.getParameterTypes());
        int pcount = ptypes.size();
        if (isMangled) {
            pcount /= 2;
        }

        // if this method is varargs we need to extract the variable arguments, place them into an
        // Object[] and create a new args array that has the varargs array in the final position
        Object[] aargs = rargs;
        // TODO: we need to differentiate between null being passed as a single argument, which
        // should be wrapped in an array, versus null being passed as the varargs array, which
        // should be passed along to the underlying method in the varargs position; this requires
        // some communication from the compiler: we need the static type of the null; for now we
        // assume they meant to pass a null array rather than a wrapped null
        if (method.isVarArgs() && aargs != null) {
            int fpcount = pcount-1, vacount = rargs.length-fpcount;
            // TEMP: we heuristically assume that if there's only one argument in the varargs
            // position and it's an array, then the caller did the wrapping already; the correct
            // thing to do is to use the static type of the single varargs argument and only
            // perform boxing if the static type is not an array type, but we don't yet reify
            // static type information into our runtime boxes
            if (vacount != 1 || (rargs[fpcount] != null && !rargs[fpcount].getClass().isArray())) {
                // the final argument position indicates the type of the varargs array
                Class<?> vatype = ptypes.get(ptypes.size()-1);
                assert vatype.getComponentType() != null : "Varargs position not array type";
                Object vargs = Array.newInstance(vatype.getComponentType(), vacount);
                System.arraycopy(rargs, fpcount, vargs, 0, rargs.length-fpcount);
                aargs = new Object[fpcount+1];
                System.arraycopy(rargs, 0, aargs, 0, fpcount);
                aargs[fpcount] = vargs;
            }
        }

        // if this method is mangled, we need to add dummy arguments in the type-carrying parameter
        // positions
        if (isMangled) {
            aargs = addMangleArgs(ptypes, (aargs == null) ? new Object[1] : aargs);
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
    protected static Method findMethod (String mname, Class<?> clazz, Class<?>[] atypes)
    {
        // TODO: this needs to follow the algorithm in JLS 15.12.2.1
        List<Method> methods = collectMethods(new ArrayList<Method>(), mname, clazz, atypes);

        if (methods.size() == 0) {
            return null; // the caller may want to fall back to an outer class
        } else if (methods.size() == 1) {
            return methods.get(0); // no ambiguity, no problem!
        }

        // look for an exact type match (simplifies life for now)
      METHOD:
        for (Method m : methods) {
            Class<?>[] ptypes = m.getParameterTypes();
            int pcount = m.getName().endsWith(MM_SUFFIX) ? ptypes.length/2 : ptypes.length;
            int poff = ptypes.length - pcount;
            for (int ii = 0, ll = Math.min(atypes.length, pcount); ii < ll; ii++) {
                Class<?> ptype = boxType(ptypes[poff+ii]);
                if (atypes[ii] != null && !ptype.equals(atypes[ii])) {
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
        return methods.get(0);
    }

    /**
     * A helper for {@link #findMethod}.
     */
    protected static List<Method> collectMethods (
        List<Method> into, String mname, Class<?> clazz, Class<?>[] atypes)
    {
        for (Method method : clazz.getDeclaredMethods()) {
            List<Class<?>> ptypes = Arrays.asList(method.getParameterTypes());
            String cmname = method.getName();
            boolean isMangled = isMangled(method);
            if (isMangled) {
                cmname = cmname.substring(0, cmname.length()-MM_SUFFIX.length());
            }
            if (cmname.equals(mname) &&
                isApplicable(ptypes, isMangled, method.isVarArgs(), atypes)) {
                into.add(method);
            }
        }
        Class<?> parent = clazz.getSuperclass();
        if (parent != null) {
            collectMethods(into, mname, parent, atypes);
        }
        return into;
    }

    /**
     * Helper for {@link #invokeStatic} and {@link #invoke}.
     */
    protected static Method checkMethod (Method m, String mname, Class<?> clazz, Object... args)
    {
        if (m == null) {
            // TODO: if argument mismatch, clarify that, if total method lacking, clarify that
            throw new NoSuchMethodError(
                Debug.format(clazz + "." + mname, "atypes", toArgTypes(args)));
        } else {
            return m;
        }
    }

    /**
     * A helper for {@link #newInstance}.
     */
    protected static Constructor<?> findConstructor (Class<?> clazz, boolean needsOuterThis,
                                                     boolean isMangled, Object... args)
    {
        Class<?>[] atypes = toArgTypes(args);
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            List<Class<?>> ptypes = Arrays.asList(ctor.getParameterTypes());
            if (needsOuterThis) {
                // ignore the outer this argument when testing for applicability
                ptypes = ptypes.subList(1, ptypes.size());
            }
            if (isApplicable(ptypes, isMangled, ctor.isVarArgs(), atypes)) {
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
        List<Class<?>> ptypes, boolean isMangled, boolean isVarArgs, Class<?>[] atypes)
    {
        int pcount = isMangled ? ptypes.size()/2 : ptypes.size();
        if (!(pcount == atypes.length || (isVarArgs && (pcount-1) <= atypes.length))) {
            return false;
        }

        // make sure all fixed arity arguments match
        int fpcount = isVarArgs ? pcount-1 : pcount, poff = isMangled ? pcount : 0;
        for (int ii = 0; ii < fpcount; ii++) {
            Class<?> ptype = /*boxType(*/ptypes.get(poff + ii)/*)*/;
            if (atypes[ii] != null && !isAssignableFrom(ptype, atypes[ii])) {
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
//                 if (atypes[ii] != null && !isAssignableFrom(ptype, atypes[ii])) {
//                     return false;
//                 }
//             }
//         }
        return true;
    }

    protected static boolean isAssignableFrom (Class<?> ptype, Class<?> atype)
    {
        return ptype.isAssignableFrom(atype) || // widening reference conversion
            boxType(ptype).equals(atype) ||     // boxing conversion
            (ptype.isPrimitive() &&             // widening primitve conversion
             COERCIONS.containsEntry(atype, ptype));
    }

    /**
     * Returns the class to which to promote both sides of a numeric operation involving the
     * supplied left- and right-hand-sides.
     */
    protected static Class<?> promote (Object lhs, Object rhs)
    {
        // if either is a double, we promote to double
        Class<?> lhc = lhs.getClass(), rhc = rhs.getClass();
        if (lhc == Double.class || rhc == Double.class) {
            return Double.class;
        }

        // if either side is a long we'll either promote to long or double
        if (lhs == Long.class) {
            return (rhc == Float.class) ? Double.class : Long.class;
        } else if (rhs == Long.class) {
            return (lhc == Float.class) ? Double.class : Long.class;
        }

        // if one side is a float, then we promote to float
        if (lhc == Float.class || rhc == Float.class) {
            return Float.class;
        }

        // otherwise we promote to int
        return Integer.class;
    }

    protected static Class<?> promoteFloat (Class<?> other)
    {
        return (other == Long.class) ? Double.class : Float.class;
    }

    protected static boolean isMangled (Method method)
    {
        return method.getName().endsWith(MM_SUFFIX);
    }

    protected static Class<?>[] toArgTypes (Object[] args)
    {
        Class<?>[] atypes = new Class<?>[args == null ? 0 : args.length];
        for (int ii = 0, ll = atypes.length; ii < ll; ii++) {
            // it'd be nice to have bottom here rather than null, alas
            atypes[ii] = (args[ii] == null) ? null : args[ii].getClass();
        }
        return atypes;
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
     * Locates and returns the value of the secret reference to the supplied non-static
     * inner-class's reference to the supplied enclosing class.
     */
    protected static Object getEnclosingReference (Class<?> clazz, Object obj)
    {
        try {
            for (Field field : obj.getClass().getDeclaredFields()) {
                if (field.getName().startsWith("this$") && field.getType() == clazz) {
                    field.setAccessible(true);
                    return field.get(obj);
                }
            }
            throw new RuntimeException(
                "Failure finding enclosing reference [class=" + obj.getClass() + "]");
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

    /** A very non-general-purpose tuple class for use as a hash key. */
    protected static class Tuple<L, R> {
        public final L left;
        public final R right;

        public static <A, B> Tuple<A, B> create (A left, B right) {
            return new Tuple<A, B>(left, right);
        }

        public Tuple (L left, R right) {
            this.left = left;
            this.right = right;
        }

        @Override public int hashCode() {
            return left.hashCode() ^ right.hashCode();
        }

        @Override public boolean equals (Object other) {
            @SuppressWarnings("unchecked") Tuple<L, R> ot = (Tuple<L, R>)other;
            return ot.left.equals(left) && ot.right.equals(right);
        }

        @Override public String toString () {
            return "(" + left + "," + right + ")";
        }
    }

    /** Used to coerce primitive types. */
    protected static interface Coercer {
        Object coerce (Object value);
    }

    /** Used to coerce a type to itself. */
    protected static class IdentityCoercer implements Coercer {
        public Object coerce (Object value) {
            return value;
        }
    }

    /** Used to coerce a numeric (non-char) type to another type. */
    protected static abstract class NumberCoercer implements Coercer {
        public Object coerce (Object value) {
            return coerce((Number)value);
        }
        protected abstract Object coerce (Number value);
    }

    protected static final BiMap<Class<?>, Class<?>> WRAPPERS =
        ImmutableBiMap.<Class<?>, Class<?>>builder().
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

    protected static final Multimap<Class<?>, Class<?>> COERCIONS = HashMultimap.create();
    static {
        // widening conversions
        COERCIONS.put(Byte.class, Short.TYPE);
        COERCIONS.put(Byte.class, Integer.TYPE);
        COERCIONS.put(Byte.class, Long.TYPE);
        COERCIONS.put(Byte.class, Float.TYPE);
        COERCIONS.put(Byte.class, Double.TYPE);
        COERCIONS.put(Short.class, Integer.TYPE);
        COERCIONS.put(Short.class, Long.TYPE);
        COERCIONS.put(Short.class, Float.TYPE);
        COERCIONS.put(Short.class, Double.TYPE);
        COERCIONS.put(Character.class, Integer.TYPE);
        COERCIONS.put(Character.class, Long.TYPE);
        COERCIONS.put(Character.class, Float.TYPE);
        COERCIONS.put(Character.class, Double.TYPE);
        COERCIONS.put(Integer.class, Long.TYPE);
        COERCIONS.put(Integer.class, Float.TYPE);
        COERCIONS.put(Integer.class, Double.TYPE);
        COERCIONS.put(Long.class, Float.TYPE);
        COERCIONS.put(Long.class, Double.TYPE);
        COERCIONS.put(Float.class, Double.TYPE);

        // widening and narrowing conversion (byte -> int -> char)
        COERCIONS.put(Byte.class, Character.TYPE);

        // narrowing conversions
        // TODO: should we insert these automatically when we see a cast?
//         COERCIONS.put(Short.class, Byte.TYPE);
//         COERCIONS.put(Short.class, Character.TYPE);
//         COERCIONS.put(Character.class, Byte.TYPE);
//         COERCIONS.put(Character.class, Short.TYPE);
//         COERCIONS.put(Integer.class, Byte.TYPE);
//         COERCIONS.put(Integer.class, Short.TYPE);
//         COERCIONS.put(Integer.class, Character.TYPE);
//         COERCIONS.put(Long.class, Byte.TYPE);
//         COERCIONS.put(Long.class, Short.TYPE);
//         COERCIONS.put(Long.class, Character.TYPE);
//         COERCIONS.put(Long.class, Integer.TYPE);
//         COERCIONS.put(Float.class, Byte.TYPE);
//         COERCIONS.put(Float.class, Short.TYPE);
//         COERCIONS.put(Float.class, Character.TYPE);
//         COERCIONS.put(Float.class, Integer.TYPE);
//         COERCIONS.put(Float.class, Long.TYPE);
//         COERCIONS.put(Double.class, Byte.TYPE);
//         COERCIONS.put(Double.class, Short.TYPE);
//         COERCIONS.put(Double.class, Character.TYPE);
//         COERCIONS.put(Double.class, Integer.TYPE);
//         COERCIONS.put(Double.class, Long.TYPE);
//         COERCIONS.put(Double.class, Float.TYPE);
    }

    protected static final Map<Tuple<? extends Class<?>,? extends Class<?>>,Coercer> COERCERS =
        ImmutableMap.<Tuple<? extends Class<?>,? extends Class<?>>,Coercer>builder().
        // coercions from byte to X
        put(Tuple.create(Byte.class, Byte.TYPE), new IdentityCoercer()).
        put(Tuple.create(Byte.class, Short.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (short)value.byteValue(); }
        }).
        put(Tuple.create(Byte.class, Character.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (char)value.byteValue(); }
        }).
        put(Tuple.create(Byte.class, Integer.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (int)value.byteValue(); }
        }).
        put(Tuple.create(Byte.class, Long.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (long)value.byteValue(); }
        }).
        put(Tuple.create(Byte.class, Float.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (float)value.byteValue(); }
        }).
        put(Tuple.create(Byte.class, Double.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (double)value.byteValue(); }
        }).
        // coercions from short to X
        put(Tuple.create(Short.class, Byte.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (byte)value.shortValue(); }
        }).
        put(Tuple.create(Short.class, Short.TYPE), new IdentityCoercer()).
        put(Tuple.create(Short.class, Character.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (char)value.shortValue(); }
        }).
        put(Tuple.create(Short.class, Integer.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (int)value.shortValue(); }
        }).
        put(Tuple.create(Short.class, Long.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (long)value.shortValue(); }
        }).
        put(Tuple.create(Short.class, Float.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (float)value.shortValue(); }
        }).
        put(Tuple.create(Short.class, Double.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (double)value.shortValue(); }
        }).
        // coercions from char to X
        put(Tuple.create(Character.class, Byte.TYPE), new Coercer() {
            public Object coerce (Object value) { return (byte)((Character)value).charValue(); }
        }).
        put(Tuple.create(Character.class, Short.TYPE), new Coercer() {
            public Object coerce (Object value) { return (short)((Character)value).charValue(); }
        }).
        put(Tuple.create(Character.class, Character.TYPE), new IdentityCoercer()).
        put(Tuple.create(Character.class, Integer.TYPE), new Coercer() {
            public Object coerce (Object value) { return (int)((Character)value).charValue(); }
        }).
        put(Tuple.create(Character.class, Long.TYPE), new Coercer() {
            public Object coerce (Object value) { return (long)((Character)value).charValue(); }
        }).
        put(Tuple.create(Character.class, Float.TYPE), new Coercer() {
            public Object coerce (Object value) { return (float)((Character)value).charValue(); }
        }).
        put(Tuple.create(Character.class, Double.TYPE), new Coercer() {
            public Object coerce (Object value) { return (double)((Character)value).charValue(); }
        }).
        // coercions from int to X
        put(Tuple.create(Integer.class, Byte.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (byte)value.intValue(); }
        }).
        put(Tuple.create(Integer.class, Short.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (short)value.intValue(); }
        }).
        put(Tuple.create(Integer.class, Character.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (char)value.intValue(); }
        }).
        put(Tuple.create(Integer.class, Integer.TYPE), new IdentityCoercer()).
        put(Tuple.create(Integer.class, Long.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (long)value.intValue(); }
        }).
        put(Tuple.create(Integer.class, Float.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (float)value.intValue(); }
        }).
        put(Tuple.create(Integer.class, Double.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (double)value.intValue(); }
        }).
        // coercions from long to X
        put(Tuple.create(Long.class, Byte.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (byte)value.longValue(); }
        }).
        put(Tuple.create(Long.class, Short.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (short)value.longValue(); }
        }).
        put(Tuple.create(Long.class, Character.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (char)value.longValue(); }
        }).
        put(Tuple.create(Long.class, Integer.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (int)value.longValue(); }
        }).
        put(Tuple.create(Long.class, Long.TYPE), new IdentityCoercer()).
        put(Tuple.create(Long.class, Float.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (float)value.longValue(); }
        }).
        put(Tuple.create(Long.class, Double.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (double)value.longValue(); }
        }).
        // coercions from float to X
        put(Tuple.create(Float.class, Byte.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (byte)value.floatValue(); }
        }).
        put(Tuple.create(Float.class, Short.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (short)value.floatValue(); }
        }).
        put(Tuple.create(Float.class, Character.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (char)value.floatValue(); }
        }).
        put(Tuple.create(Float.class, Integer.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (int)value.floatValue(); }
        }).
        put(Tuple.create(Float.class, Long.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (long)value.floatValue(); }
        }).
        put(Tuple.create(Float.class, Float.TYPE), new IdentityCoercer()).
        put(Tuple.create(Float.class, Double.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (double)value.floatValue(); }
        }).
        // coercions from double to X
        put(Tuple.create(Double.class, Byte.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (byte)value.doubleValue(); }
        }).
        put(Tuple.create(Double.class, Short.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (short)value.doubleValue(); }
        }).
        put(Tuple.create(Double.class, Character.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (char)value.doubleValue(); }
        }).
        put(Tuple.create(Double.class, Integer.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (int)value.doubleValue(); }
        }).
        put(Tuple.create(Double.class, Long.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (long)value.doubleValue(); }
        }).
        put(Tuple.create(Double.class, Float.TYPE), new NumberCoercer() {
            public Object coerce (Number value) { return (float)value.doubleValue(); }
        }).
        put(Tuple.create(Double.class, Double.TYPE), new IdentityCoercer()).
        build();
}