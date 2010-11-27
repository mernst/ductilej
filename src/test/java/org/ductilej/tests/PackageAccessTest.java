//
// $Id$

package org.ductilej.tests;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests package visibility through type parameters.
 */
public class PackageAccessTest
{
    public static class Larry<E extends Bob> {
        public E bob;

        public Larry (E bob) {
            this.bob = bob;
        }

        public String getBobName () {
            return bob.getName();
        }
    }

    public static class Bob {
        String getName () {
            return "Bob";
        }
    }

    @Test public void testPackageAccess ()
    {
        Larry<Bob> larry = new Larry<Bob>(new Bob());
        assertEquals("Bob", larry.getBobName());
    }
}
