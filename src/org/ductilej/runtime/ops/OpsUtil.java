//
// $Id$

package org.ductilej.runtime.ops;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.ductilej.runtime.BinOps;
import org.ductilej.runtime.UnOps;

/**
 * Provides access to the myriad registered operations.
 */
public class OpsUtil
{
    /**
     * Returns the {@link UnOps} instance appropriate for the supplied expression argument type.
     */
    public static UnOps get (Object arg)
    {
        // TODO: we probably want this check here, though it will hurt performance
        // if (arg == null) {
        //     throw new NullPointerException("Unary op (" + opcode + ") on null arg.");
        // }
        return UNOPS.get(arg.getClass());
    }

    /**
     * Returns the {@link BinOps} instance appropriate for the supplied left- and right-hand-sides
     * of a binary expression.
     */
    public static BinOps get (Object lhs, Object rhs)
    {
        // TODO: we probably want this check here, though it will hurt performance
        // if (lhs == null || rhs == null) {
        //     throw new NullPointerException(
        //         "Binary op (" + opcode + ") on null arg (lhs=" + lhs + ", rhs=" + rhs + ")");
        // }
        return BINOPS.get(lhs.getClass()).get(rhs.getClass());
    }

    /**
     * Compares the two instances for equality. If the instances represent primitive types,
     * implicit coercions may be performed on them as appropriate. If either instance is not a
     * coercible primitive type (non-boolean), reference equality is returned.
     */
    public static boolean isEqualTo (Object lhs, Object rhs)
    {
        if (lhs != null && rhs != null) {
            Map<Class<?>, BinOps> omap = BINOPS.get(lhs.getClass());
            if (omap != null) {
                BinOps ops = omap.get(rhs.getClass());
                if (ops != null) {
                    return ops.equalTo(lhs, rhs);
                }
            }
        }
        return (lhs == rhs);
    }

    protected static final Map<Class<?>, UnOps> UNOPS = ImmutableMap.<Class<?>, UnOps>builder().
        put(Boolean.class, new BooleanOps()).
        put(Byte.class, new ByteOps()).
        put(Short.class, new ShortOps()).
        put(Character.class, new CharacterOps()).
        put(Integer.class, new IntegerOps()).
        put(Long.class, new LongOps()).
        put(Float.class, new FloatOps()).
        put(Double.class, new DoubleOps()).
        build();

    protected static final Map<Class<?>, Map<Class<?>, BinOps>> BINOPS =
        ImmutableMap.<Class<?>, Map<Class<?>, BinOps>>builder().
        put(Boolean.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Boolean.class, new BooleanBooleanOps()).
            build()).
        put(Byte.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Byte.class, new ByteByteOps()).
            put(Short.class, new ByteShortOps()).
            put(Character.class, new ByteCharacterOps()).
            put(Integer.class, new ByteIntegerOps()).
            put(Long.class, new ByteLongOps()).
            put(Float.class, new ByteFloatOps()).
            put(Double.class, new ByteDoubleOps()).
            build()).
        put(Short.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Byte.class, new ShortByteOps()).
            put(Short.class, new ShortShortOps()).
            put(Character.class, new ShortCharacterOps()).
            put(Integer.class, new ShortIntegerOps()).
            put(Long.class, new ShortLongOps()).
            put(Float.class, new ShortFloatOps()).
            put(Double.class, new ShortDoubleOps()).
            build()).
        put(Character.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Byte.class, new CharacterByteOps()).
            put(Short.class, new CharacterShortOps()).
            put(Character.class, new CharacterCharacterOps()).
            put(Integer.class, new CharacterIntegerOps()).
            put(Long.class, new CharacterLongOps()).
            put(Float.class, new CharacterFloatOps()).
            put(Double.class, new CharacterDoubleOps()).
            build()).
        put(Integer.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Byte.class, new IntegerByteOps()).
            put(Short.class, new IntegerShortOps()).
            put(Character.class, new IntegerCharacterOps()).
            put(Integer.class, new IntegerIntegerOps()).
            put(Long.class, new IntegerLongOps()).
            put(Float.class, new IntegerFloatOps()).
            put(Double.class, new IntegerDoubleOps()).
            build()).
        put(Long.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Byte.class, new LongByteOps()).
            put(Short.class, new LongShortOps()).
            put(Character.class, new LongCharacterOps()).
            put(Integer.class, new LongIntegerOps()).
            put(Long.class, new LongLongOps()).
            put(Float.class, new LongFloatOps()).
            put(Double.class, new LongDoubleOps()).
            build()).
        put(Float.class, ImmutableMap.<Class<?>, BinOps>builder().
            put(Byte.class, new FloatByteOps()).
            put(Short.class, new FloatShortOps()).
            put(Character.class, new FloatCharacterOps()).
            put(Integer.class, new FloatIntegerOps()).
            put(Long.class, new FloatLongOps()).
            put(Float.class, new FloatFloatOps()).
            put(Double.class, new FloatDoubleOps()).
            build()).
        put(Double.class, ImmutableMap.<Class<?>, BinOps>builder().
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
