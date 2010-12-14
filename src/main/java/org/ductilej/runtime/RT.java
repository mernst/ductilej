//
// $Id$

package org.ductilej.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.primitives.*;

/**
 * Provides dynamic method dispatch, operator evaluation and other bits.
 */
public class RT
{
    /** A suffix appended to signature mangled method names. */
    public static final String MM_SUFFIX = "$M";

    /** Whether or not to use full coercions in place of implicit widenings. */
    public static boolean COERCEALL = Boolean.getBoolean("org.ductilej.coerceall");

    /**
     * Invokes the constructor of the supplied class, with the specified arguments and returns the
     * newly created instance.
     *
     * @param clazz the class to be instantiated.
     * @param encl the enclosing instance to use in the case of a non-static inner class.
     * @param args the arguments to be supplied to the constructor, if any.
     */
    public static <T> T newInstance (Class<T> clazz, Class<?>[] atypes, Object encl, Object[] args)
    {
        boolean needsOuterThis = isInnerInNonStaticContext(clazz);
        boolean isMangled = (clazz.getAnnotation(Transformed.class) != null);

        // if we were able to resolve the method at compile time, the exact argument types will be
        // provided in atypes which we can use to precise and fast(er) method lookup
        Constructor<?> ctor;
        if (atypes != null) {
            ctor = findConstructor(clazz, needsOuterThis, isMangled, atypes);
        } else {
            ctor = findConstructor(clazz, needsOuterThis, isMangled, args);
        }

        if (ctor == null) {
            // TODO: if argument mismatch, clarify that
            throw new NoSuchMethodError(Debug.format("Can't find constructor for " +
                                                     clazz.getSimpleName(), "args", args));
        }

        List<Class<?>> ptypes = Arrays.asList(ctor.getParameterTypes());
        int pcount = ptypes.size();
        if (isMangled) {
            pcount /= 2;
        }

        // if this ctor is varargs we need to extract the variable arguments, place them into an
        // Object[] and create a new args array that has the varargs array in the final position
        Object[] rargs = ctor.isVarArgs() ? collectVarArgs(ptypes, pcount, args) : args;

        // if this method is mangled, we need to add dummy arguments in the type-carrying parameter
        // positions
        if (isMangled) {
            if (needsOuterThis) {
                ptypes = ptypes.subList(1, ptypes.size());
            }
            rargs = addMangleArgs(ptypes, rargs);
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
            throw new WrappedException(ie);
        } catch (IllegalAccessException iae) {
            throw new WrappedException(iae);
        } catch (InvocationTargetException ite) {
            unwrap(ite.getCause());
            return null; // unreached
        } catch (IllegalArgumentException iae) {
            decode(iae);
            return null; // unreached
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
                // we explicitly coerce the element values to the array element type if we're
                // dealing with an array of primitives; Java allows delcarations like "byte[] foo =
                // { 1, 2, -128 }" where it automatically coerces the integers to bytes; TODO:
                // there are probably limitations on the coercion that we should emulate
                Object elem = etype.isPrimitive() ? coerce(etype, elems[ii]) : elems[ii];
                Array.set(tarray, ii, elem);
            }
            return tarray;
        } catch (ArrayStoreException ase) {
            return elems; // fall back to an object array to contain the values
        }
    }

    /**
     * Invokes the specified method via reflection, handling the necessary signature de-mangling.
     */
    public static Object invoke (String mname, Class<?>[] atypes, Object receiver, Object[] args)
    {
        if (receiver == null) {
            throw new NullPointerException(
                Debug.format("Null receiver for " + mname, "atypes", toArgTypes(args)));
        }
        Class<?> rclass = receiver.getClass();
        Method m;

        // if we were able to resolve the method at compile time, the exact argument types will be
        // provided in atypes which we can use to do precise and fast(er) method lookup
        if (atypes != null) {
            Class<?> cclass = rclass;
            do {
                m = getMethod(cclass, mname, atypes);
                if (m == null) {
                    if (!isInnerInNonStaticContext(cclass)) {
                        break;
                    }
                    cclass = cclass.getEnclosingClass();
                    receiver = getEnclosingReference(cclass, receiver);
                }
            } while (m == null);

        } else {
            // otherwise we've got to do an expensive search using the runtime argument types
            atypes = toArgTypes(args);
            Class<?> cclass = rclass;
            do {
                m = resolveMethod(cclass, mname, atypes);
                if (m == null) {
                    if (!isInnerInNonStaticContext(cclass)) {
                        break;
                    }
                    cclass = cclass.getEnclosingClass();
                    receiver = getEnclosingReference(cclass, receiver);
                }
            } while (m == null);
        }

        return invoke(checkMethod(m, mname, rclass, atypes, args), receiver, args);
    }

    /**
     * Invokes the specified static method via reflection, handling the necessary signature
     * de-mangling.
     */
    public static Object invokeStatic (String mname, Class<?>[] atypes, Class<?> clazz,
                                       Object[] args)
    {
        // if we were able to resolve the method at compile time, the exact argument types will be
        // provided in atypes which we can use to precise and fast(er) method lookup
        Method m = (atypes != null) ? getMethod(clazz, mname, atypes) :
            // otherwise we've got to do an expensive search using the runtime argument types
            resolveMethod(clazz, mname, toArgTypes(args));
        return invoke(checkMethod(m, mname, clazz, atypes, args), null, args);
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
            Field field = getField(clazz, fname);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException nsfe) {
            throw new WrappedException(nsfe);
        } catch (IllegalAccessException iae) {
            throw new WrappedException(iae);
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
            Field field = getField(target.getClass(), fname);
            field.setAccessible(true);
            field.set(target, value);
            return value; // TODO: is the result of assignment the coerced type? in that case we
                          // need to return field.get(fname)

        } catch (NoSuchFieldException nsfe) {
            throw new WrappedException(nsfe);
        } catch (IllegalAccessException iae) {
            throw new WrappedException(iae);
        }
    }

    /**
     * Casts an object to an iterable over objects. Used to massage foreach expressions.
     */
    public static Iterable<?> asIterable (final Object arg)
    {
        if (arg == null) {
            throw new NullPointerException("Null iterable in foreach?");
        } else if (arg instanceof Object[]) {
            return Arrays.asList((Object[])arg);
        } else if (arg instanceof Iterable<?>) {
            @SuppressWarnings("unchecked") Iterable<Object> casted = (Iterable<Object>)arg;
            return casted;
        } else if (arg instanceof boolean[]) {
            return Booleans.asList((boolean[])arg);
        } else if (arg instanceof byte[]) {
            return Bytes.asList((byte[])arg);
        } else if (arg instanceof char[]) {
            return Chars.asList((char[])arg);
        } else if (arg instanceof short[]) {
            return Shorts.asList((short[])arg);
        } else if (arg instanceof int[]) {
            return Ints.asList((int[])arg);
        } else if (arg instanceof long[]) {
            return Longs.asList((long[])arg);
        } else if (arg instanceof float[]) {
            return Floats.asList((float[])arg);
        } else if (arg instanceof double[]) {
            return Doubles.asList((double[])arg);
        } else {
            // if none of those things matched, just try wrapping the object in a proxy that treats
            // it as an Iterable
            return (Iterable<?>)asInterface(Iterable.class, arg);
        }
    }

    /**
     * Performs assignment of the specified value into the specified array at the specified index.
     * Returns the assigned value.
     */
    public static Object assignAt (Object array, Object index, Object value)
    {
        if (array.getClass().getComponentType().isPrimitive()) {
            Array.set(array, asInt(index), value);
        } else {
            // Array.set throws IllegalArgumentException instead of ArrayStoreException when
            // assigning a value of incorrect type to an element, so we don't use it for
            // non-primitive arrays
            ((Object[])array)[asInt(index)] = value;
        }
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
     * Performs primitive numeric widening on a value.
     */
    public static Object widen (Class<?> clazz, Object value)
    {
        if (COERCEALL) {
            return coerce(clazz, value);
        }
        if (value == null) {
            throw new NullPointerException("Cannot widen null to " + clazz.getName());
        }
        // TODO: differentiate between coercers and wideners
        Coercer c = COERCERS.get(Tuple.create(value.getClass(), clazz));
        if (c == null) {
            throw new IllegalArgumentException(
                "Cannot widen " + value.getClass().getName() + " to " + clazz.getName());
        }
        return c.coerce(value);
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
            Debug.warn("Runtime cast failure", "target", clazz, "value", value, "vclass", vclass);
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
            Debug.warn("Runtime cast failure", "target", clazz, "value", value, "vclass", vclass);
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
     * Creates a proxy that implements the supplied interface by calling through to methods with
     * the same name and parameter types in the supplied underlying object.
     */
    public static <T> T asInterface (Class<T> iface, final Object inst)
    {
        Preconditions.checkNotNull(iface);
        Preconditions.checkNotNull(inst);

        Object pinst = Proxy.newProxyInstance(
            iface.getClassLoader(), new Class<?>[] { iface }, new InvocationHandler() {
            public Object invoke (Object proxy, Method method, Object[] args) {
                // find the method to which this interface method maps (using a cache)
                Method target = _meths.get(method);
                if (target == null) {
                    target = findProxyMethod(inst.getClass(), method.getName(),
                                             method.getParameterTypes(),
                                             Lists.<Method>newArrayList());
                    target.setAccessible(true);
                    _meths.put(method, target);
                }

                // if this method is mangled, we need to add dummy arguments in the type-carrying
                // parameter positions
                if (isMangled(target.getName())) {
                    args = addMangleArgs(Arrays.asList(target.getParameterTypes()), args);
                }

                // finally invoke the method
                return checkedInvoke(target, inst, args);
            }
            protected Map<Method, Method> _meths = Maps.newHashMap();
        });

        @SuppressWarnings("unchecked") T proxy = (T)pinst;
        return proxy;
    }

    /**
     * A helper method for initializing primitive values.
     *
     * It is supplied the default value
     * appropriate to the type of the primitive field and the field's current value. If the
     * (detyped) field has not yet been initialized, curVal will be null and the appropriate
     * initial value will be returned. Otherwise the current value will be returned.
     *
     * <p> This preserves correct behavior in situations where primitive fields are initialized by
     * abstract methods called by a parent class constructor, because our primitive initialization
     * code runs (of necessity) after the superclass constructor.
     */
    public static Object initPrimitive (Object target, String fieldName, Class<?> fieldType)
    {
        Object curVal = select(fieldName, target);
        return (curVal != null) ? curVal : DEFAULT_VALUES.get(fieldType);
    }

    /**
     * A helper for {@link #select} and {@link #assign}.
     *
     * @throws NoSuchFieldException if the field could not be found.
     */
    protected static Field getField (Class<?> clazz, String fname)
        throws NoSuchFieldException
    {
        FieldKey key = new FieldKey(clazz, fname);
        Field field = _fieldCache.get(key);
        if (field == null) {
            _fieldCache.put(key, field = findField(clazz, fname));
        }
        return field;
    }

    /**
     * A helper for {@link #getField}.
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
    protected static Object invoke (Method method, Object receiver, Object[] rargs)
    {
        boolean isMangled = isMangled(method.getName());
        List<Class<?>> ptypes = Arrays.asList(method.getParameterTypes());
        int pcount = ptypes.size();
        if (isMangled) {
            pcount /= 2;
        }

        // if this method is varargs we need to extract the variable arguments, place them into an
        // Object[] and create a new args array that has the varargs array in the final position
        Object[] aargs = method.isVarArgs() ? collectVarArgs(ptypes, pcount, rargs) : rargs;

        // if this method is mangled, we need to add dummy arguments in the type-carrying parameter
        // positions
        if (isMangled) {
            aargs = addMangleArgs(ptypes, (aargs == null) ? new Object[1] : aargs);
        }

        // for any argument position that does not match, but which takes an interface, create a
        // proxy to that interface using the actual argument
        if (!isMangled) {
            for (int ii = 0; ii < pcount; ii++) {
                if (ptypes.get(ii).isInterface() && !ptypes.get(ii).isInstance(aargs[ii])) {
                    aargs[ii] = asInterface(ptypes.get(ii), aargs[ii]);
                }
            }
        }

        // Debug.temp("Invoking " + method, "recv", receiver, "args", aargs);
        method.setAccessible(true); // TODO: cache which methods we've toggled if slow
        return checkedInvoke(method, receiver, aargs);
    }

    /**
     * A helper for {@link #newInstance}.
     */
    protected static Constructor<?> findConstructor (
        Class<?> clazz, boolean needsOuterThis, boolean isMangled, Class<?>[] atypes)
    {
        MethodKey key = new MethodKey(clazz, "<init>", atypes);
        Constructor<?> cached = _ctorCache.get(key);
        if (cached != null) {
            return cached;
        }

      OUTER:
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] ptypes = ctor.getParameterTypes();
            int poff = isMangled ? ptypes.length/2 : 0;
            // ignore the outer this argument when testing for applicability
            if (needsOuterThis) {
                poff += 1;
            }
            if (ptypes.length - poff != atypes.length) {
                continue;
            }
            for (int ii = 0; ii < atypes.length; ii++) {
                if (ptypes[ii+poff] != atypes[ii]) {
                    continue OUTER;
                }
            }
            _ctorCache.put(key, ctor);
            return ctor;
        }
        return null;
    }

    /**
     * A helper for {@link #invoke(Method,Object,Object[])}.
     */
    protected static Method getMethod (Class<?> clazz, String mname, Class<?>[] atypes)
    {
        MethodKey key = new MethodKey(clazz, mname, atypes);
        Method method = _methodCache.get(key);
        if (method == null) {
            // find method may return null, in which case we'll cache that this method was
            // unresolvable and immediately return so on future invocations
            _methodCache.put(key, method = findMethod(clazz, mname, atypes));
        }
        return method;
    }

    /**
     * A helper for {@link #getMethod}.
     */
    protected static Method findMethod (Class<?> clazz, String mname, Class<?>[] atypes)
    {
      OUTER:
        for (Method method : clazz.getDeclaredMethods()) {
            String cmname = method.getName();
            boolean isMangled = isMangled(cmname);
            if (isMangled) {
                cmname = cmname.substring(0, cmname.length()-MM_SUFFIX.length());
            }
            if (!cmname.equals(mname)) {
                continue;
            }
            Class<?>[] ptypes = method.getParameterTypes();
            int poff = isMangled ? ptypes.length/2 : 0;
            if (ptypes.length - poff != atypes.length) {
                continue;
            }
            for (int ii = 0; ii < atypes.length; ii++) {
                if (ptypes[ii+poff] != atypes[ii]) {
                    continue OUTER;
                }
            }
            return method;
        }
        Class<?> parent = clazz.getSuperclass();
        return (parent == null) ? null : findMethod(parent, mname, atypes);
    }

    /**
     * Resolves the best matching method given the supplied runtime argument types. This is used
     * for methods that could not be resolved at compile time and thus for which we do not have to
     * preserve method resolution equivalent equivalent to that done for type correct code.
     */
    protected static Method resolveMethod (Class<?> clazz, String mname, Class<?>[] atypes)
    {
        MethodData mdata = new MethodData();
        resolveMethod(clazz, mname, atypes, mdata);
        return mdata.best;
    }

    protected static void resolveMethod (Class<?> clazz, String mname, Class<?>[] atypes,
                                         MethodData mdata)
    {
        for (Method method : clazz.getDeclaredMethods()) {
            String cmname = method.getName();
            boolean isMangled = isMangled(cmname);
            if (isMangled) {
                cmname = cmname.substring(0, cmname.length()-MM_SUFFIX.length());
            }
            if (!cmname.equals(mname)) {
                continue;
            }

            Class<?>[] ptypes = method.getParameterTypes();
            if (!argCountMatch(isMangled, method.isVarArgs(), ptypes.length, atypes.length)) {
                continue;
            }

            Match match = argTypeMatch(
                Arrays.asList(ptypes), isMangled, method.isVarArgs(), atypes);
            if (match == Match.NONE) {
                // no match, keep looking

            } else if (match.isCloser(mdata.match)) {
                mdata.best = method;
                mdata.match = match;

            } else if (match == mdata.match) {
                // if the argument types are exactly the same and the declaring class differs,
                // we're just seeing a parent method that has been overridden by our best match
                if (!mdata.best.getDeclaringClass().equals(clazz) &&
                    Arrays.equals(mdata.best.getParameterTypes(), ptypes)) {
                    continue;
                }
                throw new AmbiguousMethodError(
                    Debug.format("Two methods (or more) with matching types", "mname", mname,
                                 "atypes", atypes, "m1", mdata.best, "m2", method));
            } // else: keep our existing match, it's closer
        }

        Class<?> parent = clazz.getSuperclass();
        if (parent != null) {
            resolveMethod(parent, mname, atypes, mdata);
        }
    }

    /**
     * A helper for {@link #asInterface}.
     */
    protected static Method findProxyMethod (
        Class<?> clazz, String mname, Class<?>[] atypes, List<Method> candidates)
    {
      OUTER:
        for (Method method : clazz.getDeclaredMethods()) {
            String cmname = method.getName();
            boolean isMangled = isMangled(cmname);
            if (isMangled) {
                cmname = cmname.substring(0, cmname.length()-MM_SUFFIX.length());
            }
            if (!cmname.equals(mname)) {
                continue;
            }
            Class<?>[] ptypes = method.getParameterTypes();
            int poff = isMangled ? ptypes.length/2 : 0;
            if (ptypes.length - poff != atypes.length) {
                continue;
            }
            for (int ii = 0; ii < atypes.length; ii++) {
                if (ptypes[ii+poff] != atypes[ii]) {
                    candidates.add(method);
                    continue OUTER;
                }
            }
            return method;
        }

        // if we have a superclass, check it for an exact match (or additional candidates)
        Class<?> parent = clazz.getSuperclass();
        if (parent != null) {
            return findProxyMethod(parent, mname, atypes, candidates);
        }

        switch (candidates.size()) {
        case 1:
            return candidates.get(0);
        case 0:
            throw new NoSuchMethodError(Debug.format(clazz + "." + mname, "atypes", atypes));
        default:
            throw new AmbiguousMethodError(
                Debug.format("No exact match and multiple inexact matches for method",
                             "mname", mname, "atypes", atypes));
        }
    }

    /**
     * Helper for {@link #invokeStatic} and {@link #invoke}.
     */
    protected static Method checkMethod (
        Method m, String mname, Class<?> clazz, Class<?>[] atypes, Object[] args)
    {
        if (m == null) {
            // TODO: if argument mismatch, clarify that, if total method lacking, clarify that
            throw new NoSuchMethodError(
                Debug.format(clazz + "." + mname, "ftypes", atypes, "atypes", toArgTypes(args)));
        } else {
            return m;
        }
    }

    /**
     * Helper for {@link #invoke} and {@link #asInterface}.
     */
    protected static Object checkedInvoke (Method method, Object receiver, Object[] args)
    {
        try {
            return method.invoke(receiver, args);
        } catch (IllegalAccessException iae) {
            throw new WrappedException(iae);
        } catch (InvocationTargetException ite) {
            unwrap(ite.getCause());
            return null; // unreached
        } catch (IllegalArgumentException iae) {
            decode(iae);
            return null; // unreached
        }
    }

    /**
     * A helper for {@link #newInstance}.
     */
    protected static Constructor<?> findConstructor (
        Class<?> clazz, boolean needsOuterThis, boolean isMangled, Object[] args)
    {
        Constructor<?> best = null;
        Match bestMatch = Match.NONE;

        Class<?>[] atypes = toArgTypes(args);
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            List<Class<?>> ptypes = Arrays.asList(ctor.getParameterTypes());
            if (needsOuterThis) {
                // ignore the outer this argument when testing for applicability
                ptypes = ptypes.subList(1, ptypes.size());
            }

            if (!argCountMatch(isMangled, ctor.isVarArgs(), ptypes.size(), atypes.length)) {
                continue;
            }

            Match match = argTypeMatch(ptypes, isMangled, ctor.isVarArgs(), atypes);
            if (match == Match.NONE) {
                // no match, keep looking

            } else if (match.isCloser(bestMatch)) {
                best = ctor;
                bestMatch = match;

            } else if (match == bestMatch) {
                throw new AmbiguousMethodError(
                    Debug.format("Two (or more) matching ctors", "clazz", clazz.getName(),
                                 "atypes", atypes, "c1", best, "c2", ctor));
            } // else: existing match is better, move it along
        }

        return best;
    }

    protected static boolean argCountMatch (
        boolean isMangled, boolean isVarArgs, int pcount, int acount)
    {
        if (isMangled) {
            // we use this in favor of pcount/=2 because sometimes the compiler inserts synthetic
            // ctors which tack an argument onto a mangled method (giving it an odd number of
            // arguments, which would otherwise cause confusion); TODO: find out why they're added
            pcount -= pcount / 2;
        }
        return (pcount == acount) || (isVarArgs && (pcount-1) <= acount);
    }

    protected static Match argTypeMatch (
        List<Class<?>> ptypes, boolean isMangled, boolean isVarArgs, Class<?>[] atypes)
    {
        // determine whether all fixed arity arguments match
        int pcount = isMangled ? ptypes.size()/2 : ptypes.size();
        int fpcount = isVarArgs ? pcount-1 : pcount, poff = isMangled ? pcount : 0;
        Match match = Match.EXACT; // assume exact match
        for (int ii = 0; ii < fpcount; ii++) {
            Class<?> atype = atypes[ii];
            if (atype == null) {
                continue;
            }
            Class<?> ptype = ptypes.get(poff + ii);
            if (atype == ptype) {
                continue; // exact match, no conversion demotion
            }
            if (ptype.isAssignableFrom(atype)) {
                match = match.lesser(Match.SUBTYPE); // reduce to subtype conversion
                continue;
            }
            if (boxType(ptype).equals(atype) ||
                (ptype.isPrimitive() && COERCIONS.containsEntry(atype, ptype))) {
                match = match.lesser(Match.CONVERT); // reduce to boxing/widening conversion
                continue;
            }
            if (ptype.isInterface()) {
                match = match.lesser(Match.PROXIED); // we'll proxy this interface
                continue;
            }
            return Match.NONE; // argument mismatch
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
        return match;
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

    protected static boolean isMangled (String name)
    {
        return name.endsWith(MM_SUFFIX);
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

    protected static Object[] collectVarArgs (List<Class<?>> ptypes, int pcount, Object[] rargs)
    {
        // if the caller is passing null in the varargs position, we need to wrap that in an object
        // array so that when we pass it to Method.invoke, the null is properly passed on to the
        // underlying varargs method
        if (rargs == null) {
            return new Object[] { null };
        }

        // if we have more than one argument in varargs position or we have a non-array in varargs
        // position, we need to wrap the varargs into an array (this is normally done by javac); we
        // heuristically assume that if there's only one argument in the varargs position and it's
        // an array, then the caller did the wrapping already
        int fpcount = pcount-1, vacount = rargs.length-fpcount;
        if (vacount != 1 || (rargs[fpcount] != null && !rargs[fpcount].getClass().isArray())) {
            // the final argument position indicates the type of the varargs array
            Class<?> vatype = ptypes.get(ptypes.size()-1);
            assert vatype.getComponentType() != null : "Varargs position not array type";
            Object vargs = Array.newInstance(vatype.getComponentType(), vacount);
            System.arraycopy(rargs, fpcount, vargs, 0, rargs.length-fpcount);
            Object[] aargs = new Object[fpcount+1];
            System.arraycopy(rargs, 0, aargs, 0, fpcount);
            aargs[fpcount] = vargs;
            return aargs;
        }

        return rargs;
    }

    protected static Object[] addMangleArgs (List<Class<?>> ptypes, Object[] args)
    {
        Object[] margs = new Object[args.length*2];
        System.arraycopy(args, 0, margs, 0, args.length);
        for (int ii = args.length; ii < ptypes.size(); ii++) {
            // if the argument is a primitive type, DEFAULT_VALUES will contain a suitable dummy
            // value for that type, otherwise it will return null which is the desired dummy value
            // for all non-primitive types
            margs[ii] = DEFAULT_VALUES.get(ptypes.get(ii));
            // if the argument type is primitive, insert a coercion from the argument type to the
            // parameter type (JLS 5.3 requires widening; TODO: we're also doing narrowing here
            // which is probably not desirable in the long term)
            int aii = ii-args.length;
            if (margs[aii] != null && margs[ii] != null) {
                margs[aii] = widen(ptypes.get(ii), margs[aii]);
            }
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

    protected static void unwrap (Throwable t)
    {
        if (t instanceof WrappedException) {
            unwrap(t.getCause());
        } else if (t instanceof Error) {
            throw (Error)t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException)t;
        } else {
            throw new WrappedException(t);
        }
    }

    protected static void decode (IllegalArgumentException iae)
    {
        // annoyingly, Method.invoke and Constructor.newInstance report argument type mismatch with
        // an IllegalArgumentException with no cause, but whose message contains the results of
        // calling toString() on the underlying ClassCastException; wacky
        if (iae.getMessage().startsWith("java.lang.ClassCastException")) {
            throw (ClassCastException)new ClassCastException().initCause(iae);
        } else {
            throw iae;
        }
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

    /** Used to resolve the closest matching method or ctor. */
    protected enum Match {
        // these must be in order of precedence from lowest to highest
        NONE, PROXIED, CONVERT, SUBTYPE, EXACT;

        public boolean isCloser (Match other) {
            return ordinal() > other.ordinal();
        }

        public Match lesser (Match other) {
            return isCloser(other) ? other : this;
        }
    };

    /** Used to resolve the closest matching method or ctor. */
    protected static class MethodData
    {
        public Method best;
        public Match match = Match.NONE;
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

    protected static class MethodKey {
        public final Class<?> clazz;
        public final String name;
        public final Class<?>[] atypes;

        public MethodKey (Class<?> clazz, String name, Class<?>[] atypes) {
            this.clazz = clazz;
            this.name = name.intern();
            this.atypes = atypes;

            // precompute our hashcode as it is non-trivial to compute
            int code = clazz.hashCode();
            code = code * 31 + name.hashCode();
            for (Class<?> atype : atypes) {
                code = code * 31 + atype.hashCode();
            }
            _hashCode = code;
        }

        @Override public boolean equals (Object other) {
            MethodKey okey = (MethodKey)other;
            if (okey.clazz == clazz && okey.name == name && okey.atypes.length == atypes.length) {
                for (int ii = 0, ll = atypes.length; ii < ll; ii++) {
                    if (atypes[ii] != okey.atypes[ii]) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Override public int hashCode() {
            return _hashCode;
        }

        protected final int _hashCode;
    }

    protected static class FieldKey {
        public final Class<?> clazz;
        public final String name;

        public FieldKey (Class<?> clazz, String name) {
            this.clazz = clazz;
            this.name = name.intern();
        }

        @Override public boolean equals (Object other) {
            FieldKey okey = (FieldKey)other;
            return (okey.clazz == clazz && okey.name == name);
        }

        @Override public int hashCode() {
            return clazz.hashCode() * 31 + name.hashCode();
        }
    }

    protected static Map<MethodKey, Constructor<?>> _ctorCache = Maps.newHashMap();
    protected static Map<MethodKey, Method> _methodCache = Maps.newHashMap();
    protected static Map<FieldKey, Field> _fieldCache = Maps.newHashMap();

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

    protected static final Map<Class<?>, Object> DEFAULT_VALUES =
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
        // coercions from boolean to boolean
        put(Tuple.create(Boolean.class, Boolean.TYPE), new IdentityCoercer()).
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
