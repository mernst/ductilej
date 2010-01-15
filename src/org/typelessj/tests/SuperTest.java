//
// $Id$

package org.typelessj.tests;

import java.awt.Button;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of super constructors and super method calls.
 */
public class SuperTest
{
    public static class A {
        public final int value;
        public final String text;

        public A (int value, String text) {
            this.value = value;
            this.text = text;
        }
    }

    public static class B extends A {
        public B (int value) {
            super(value, "text");
        }
    }

    public static class StringList extends ArrayList<String>
    {
        public StringList (Collection<String> values) {
            super(values); // TODO: picks the wrong static overload
        }
    }

    public static class TestButton extends Button
    {
        public TestButton (String text) {
            super(text);
        }
    }
    
    @Test public void testSuperConstructor ()
    {
        B b = new B(5);
        assertEquals(b.value, 5);
        assertEquals(b.text, "text");

//         StringList slist = new StringList(Collections.singleton("Hello"));
//         assertEquals(slist.get(0), "Hello");

        TestButton button = new TestButton("test");
        assertEquals(button.getLabel(), "test");
    }
}
