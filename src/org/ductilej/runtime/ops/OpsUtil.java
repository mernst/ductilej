//
// $Id$

package org.ductilej.runtime.ops;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.ductilej.runtime.Ops;

/**
 * Provides access to the myriad registered operations.
 */
public class OpsUtil
{
    /**
     * Returns the {@link Ops} instance appropriate for the supplied left- and right-hand-sides of
     * a binary expression.
     */
    public static Ops get (Object lhs, Object rhs)
    {
        return COERCERS.get(lhs.getClass()).get(rhs.getClass());
    }

    /**
     * Compares the two instances for equality. If the instances represent primitive types,
     * implicit coercions may be performed on them as appropriate. If either instance is not a
     * coercible primitive type (non-boolean), reference equality is returned.
     */
    public static boolean isEqualTo (Object lhs, Object rhs)
    {
        if (lhs != null && rhs != null) {
            Map<Class<?>, Ops> omap = COERCERS.get(lhs.getClass());
            if (omap != null) {
                Ops ops = omap.get(rhs.getClass());
                if (ops != null) {
                    return ops.equalTo(lhs, rhs);
                }
            }
        }
        return (lhs == rhs);
    }

    protected static final Map<Class<?>, Map<Class<?>, Ops>> COERCERS =
        ImmutableMap.<Class<?>, Map<Class<?>, Ops>>builder().
        put(Byte.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new ByteByteOps()).
            put(Short.class, new ByteShortOps()).
            put(Character.class, new ByteCharacterOps()).
            put(Integer.class, new ByteIntegerOps()).
            put(Long.class, new ByteLongOps()).
            put(Float.class, new ByteFloatOps()).
            put(Double.class, new ByteDoubleOps()).
            build()).
        put(Short.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new ShortByteOps()).
            put(Short.class, new ShortShortOps()).
            put(Character.class, new ShortCharacterOps()).
            put(Integer.class, new ShortIntegerOps()).
            put(Long.class, new ShortLongOps()).
            put(Float.class, new ShortFloatOps()).
            put(Double.class, new ShortDoubleOps()).
            build()).
        put(Character.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new CharacterByteOps()).
            put(Short.class, new CharacterShortOps()).
            put(Character.class, new CharacterCharacterOps()).
            put(Integer.class, new CharacterIntegerOps()).
            put(Long.class, new CharacterLongOps()).
            put(Float.class, new CharacterFloatOps()).
            put(Double.class, new CharacterDoubleOps()).
            build()).
        put(Integer.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new IntegerByteOps()).
            put(Short.class, new IntegerShortOps()).
            put(Character.class, new IntegerCharacterOps()).
            put(Integer.class, new IntegerIntegerOps()).
            put(Long.class, new IntegerLongOps()).
            put(Float.class, new IntegerFloatOps()).
            put(Double.class, new IntegerDoubleOps()).
            build()).
        put(Long.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new LongByteOps()).
            put(Short.class, new LongShortOps()).
            put(Character.class, new LongCharacterOps()).
            put(Integer.class, new LongIntegerOps()).
            put(Long.class, new LongLongOps()).
            put(Float.class, new LongFloatOps()).
            put(Double.class, new LongDoubleOps()).
            build()).
        put(Float.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new FloatByteOps()).
            put(Short.class, new FloatShortOps()).
            put(Character.class, new FloatCharacterOps()).
            put(Integer.class, new FloatIntegerOps()).
            put(Long.class, new FloatLongOps()).
            put(Float.class, new FloatFloatOps()).
            put(Double.class, new FloatDoubleOps()).
            build()).
        put(Double.class, ImmutableMap.<Class<?>, Ops>builder().
            put(Byte.class, new DoubleByteOps()).
            put(Short.class, new DoubleShortOps()).
            put(Character.class, new DoubleCharacterOps()).
            put(Integer.class, new DoubleIntegerOps()).
            put(Long.class, new DoubleLongOps()).
            put(Float.class, new DoubleFloatOps()).
            put(Double.class, new DoubleDoubleOps()).
            build()).
        build();
}
