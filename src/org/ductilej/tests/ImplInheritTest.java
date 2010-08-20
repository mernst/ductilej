//
// $Id$

package org.ductilej.tests;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.zip.Deflater;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests removal of "implements" declarations for application interfaces, thereby avoiding a
 * problem where a class that implements an application interface but inherits the implementation
 * of one or more methods from a library parent class ends up no longer correctly implementing the
 * interface because the interface was detyped but the inherited library methods were not.
 */
public class ImplInheritTest
{
    public interface HasEnding {
        public boolean finished ();
        public int progress ();
    }

    public class TestImpl extends Deflater implements HasEnding {
        // boolean finished() is inherited from Deflater

        @Override // @Override will need to be stripped away
        public int progress () {
            return 0;
        }
    }

    public interface Cache {
        public int size ();
    }

    public class MyCache extends HashMap<String, String> implements Cache {
        // this will not be detyped because it overrides an implementation in Map, but it needs to
        // be detyped because it's supposed to implement Cache.size, which has itself been detyped
        @Override public int size () {
            return super.size();
        }
    }

    @Test public void testNoop () {
        HasEnding obj = new TestImpl();
        assertEquals(0, obj.progress());
    }
}
