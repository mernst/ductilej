//
// $Id$

package org.ductilej.runtime;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.ductilej.runtime.ops.*;

/**
 * Used to dispatch unary operations at runtime.
 */
public enum Unop
{
    UNARY_MINUS {
        public Object invoke (Object arg) {
            return get(arg).minus(arg);
        }
    },
    UNARY_PLUS {
        public Object invoke (Object arg) {
            return get(arg).plus(arg);
        }
    },

    BITWISE_COMPLEMENT {
        public Object invoke (Object arg) {
            return get(arg).bitComp(arg);
        }
    },
    LOGICAL_COMPLEMENT {
        public Object invoke (Object arg) {
            return get(arg).logicalComp(arg);
        }
    },

    // the side effects for these operations are handled by rewriting the AST, so they simply
    // need to return an incremented or decremented value
    PREFIX_INCREMENT {
        public Object invoke (Object arg) {
            return get(arg).increment(arg);
        }
    },
    POSTFIX_INCREMENT {
        public Object invoke (Object arg) {
            return get(arg).increment(arg);
        }
    },
    PREFIX_DECREMENT {
        public Object invoke (Object arg) {
            return get(arg).decrement(arg);
        }
    },
    POSTFIX_DECREMENT {
        public Object invoke (Object arg) {
            return get(arg).decrement(arg);
        }
    };

    /**
     * Executes this unary operation.
     */
    public abstract Object invoke (Object arg);

    /**
     * Returns the {@link UnOps} instance appropriate for the supplied expression argument type.
     */
    protected UnOps get (Object arg)
    {
        try {
            return UNOPS.get(arg.getClass());
        } catch (NullPointerException npe) {
            throw new NullPointerException("Unary op (" + this + ") on null arg.");
        }
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
}
