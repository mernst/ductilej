//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests (non-)transformation of hashcode signature.
 */
public class HashCodeTest
{
    public class Key
    {
        public /* final TODO: this hoses the constructor */ int one, two;

        public Key (int one, int two) {
            this.one = one;
            this.two = two;
        }

        @Override
        public boolean equals (Object other) {
            return (other instanceof Key) &&
                ((Key)other).one == this.one &&
                ((Key)other).two == this.two;
        }

        @Override
        public int hashCode () {
            return 37 * this.one + this.two;
        }

        @Override
        public String toString () {
            return "[" + this.one + ", " + this.two + "]";
        }
    }

    @Test
    public void hashCodeTest ()
    {
        Key key = new Key(42, -24);
        org.junit.Assert.assertTrue(key.hashCode() == (-24 + 37 * 42));
        assertTrue(key.hashCode() == (37 * 42 + -24));
    }
}
