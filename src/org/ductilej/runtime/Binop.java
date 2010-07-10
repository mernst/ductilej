//
// $Id$

package org.ductilej.runtime;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.ductilej.runtime.ops.*;

/**
 * Used to dispatch binary operations at runtime.
 */
public enum Binop {
    EQUAL_TO {
        public Object invoke (Object lhs, Object rhs) {
            return isEqualTo(lhs, rhs);
        }
    },
    NOT_EQUAL_TO {
        public Object invoke (Object lhs, Object rhs) {
            return !isEqualTo(lhs, rhs);
        }
    },

    PLUS {
        public Object invoke (Object lhs, Object rhs) {
            if (lhs instanceof String || rhs instanceof String) {
                return String.valueOf(lhs) + String.valueOf(rhs);
            } else {
                return get(lhs, rhs).plus(lhs, rhs);
            }
        }
    },
    MINUS {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).minus(lhs, rhs);
        }
    },
    MULTIPLY {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).multiply(lhs, rhs);
        }
    },
    DIVIDE {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).divide(lhs, rhs);
        }
    },
    REMAINDER {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).remainder(lhs, rhs);
        }
    },

    LESS_THAN {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).lessThan(lhs, rhs);
        }
    },
    GREATER_THAN {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).greaterThan(lhs, rhs);
        }
    },
    LESS_THAN_EQUAL {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).lessThanEq(lhs, rhs);
        }
    },
    GREATER_THAN_EQUAL {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).greaterThanEq(lhs, rhs);
        }
    },
        
    OR {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).bitOr(lhs, rhs);
        }
    },
    AND {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).bitAnd(lhs, rhs);
        }
    },
    XOR {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).bitXor(lhs, rhs);
        }
    },

    LEFT_SHIFT {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).leftShift(lhs, rhs);
        }
    },
    RIGHT_SHIFT {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).rightShift(lhs, rhs);
        }
    },
    UNSIGNED_RIGHT_SHIFT {
        public Object invoke (Object lhs, Object rhs) {
            return get(lhs, rhs).unsignedRightShift(lhs, rhs);
        }
    },

    CONDITIONAL_AND {
        public Object invoke (Object lhs, Object rhs) {
            throw new IllegalArgumentException("&& should not be lifted");
        }
    },
    CONDITIONAL_OR {
        public Object invoke (Object lhs, Object rhs) {
            throw new IllegalArgumentException("|| should not be lifted");
        }
    };

    // conditional and and or are not detyped
    // CONDITIONAL_AND
    // CONDITIONAL_OR

    // the assignment operators are transformed into non-assignment versions by the detyper
    // MULTIPLY_ASSIGNMENT
    // DIVIDE_ASSIGNMENT
    // REMAINDER_ASSIGNMENT
    // PLUS_ASSIGNMENT
    // MINUS_ASSIGNMENT
    // LEFT_SHIFT_ASSIGNMENT
    // RIGHT_SHIFT_ASSIGNMENT
    // UNSIGNED_RIGHT_SHIFT_ASSIGNMENT
    // AND_ASSIGNMENT
    // XOR_ASSIGNMENT
    // OR_ASSIGNMENT

    /**
     * Executes this operation.
     */
    public abstract Object invoke (Object lhs, Object rhs);

    /**
     * Returns the {@link BinOps} instance appropriate for the supplied left- and right-hand-sides
     * of a binary expression.
     */
    protected BinOps get (Object lhs, Object rhs)
    {
        try {
            return BINOPS.get(lhs.getClass()).get(rhs.getClass());
        } catch (NullPointerException npe) {
            throw new NullPointerException(
                "Binary op (" + this + ") on null arg (lhs=" + lhs + ", rhs=" + rhs + ")");
        }
    }

    /**
     * Compares the two instances for equality. If the instances represent primitive types,
     * implicit coercions may be performed on them as appropriate. If either instance is not a
     * coercible primitive type (non-boolean), reference equality is returned.
     */
    protected static boolean isEqualTo (Object lhs, Object rhs)
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
