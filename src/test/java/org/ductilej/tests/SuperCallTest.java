//
// $Id$

package org.ductilej.tests;

import java.awt.Button;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of super constructors and super method calls.
 */
public class SuperCallTest
{
    public static class A {
        public final int value;
        public final String text;

        public A (int value, String text) {
            this.value = value;
            this.text = text;
        }

        public String who () {
            return "A";
        }

        public String why (String reason) {
            return reason;
        }
    }

    public static class B extends A {
        public B (int value) {
            super(value, "text");
        }

        @Override public String who () {
            return "B";
        }

        public String superwho () {
            return super.who();
        }

        public String why (String reason) {
            return "Because " + super.why(reason);
        }
    }

    public static class StringList extends ArrayList<String>
    {
        public StringList (Collection<String> values) {
            super(values);
        }
    }

    public static class TestButton extends Button
    {
        public TestButton (String text) {
            super(text);
        }

        public void updateLabel (String text) {
            super.setLabel(text);
        }
    }
    
    @Test public void testSuperConstructor ()
    {
        B b = new B(5);
        assertEquals(5, b.value);
        assertEquals("text", b.text);

        assertEquals("B", b.who());
        assertEquals("A", b.superwho());

        assertEquals("Because me", b.why("me"));

        StringList slist = new StringList(Collections.singleton("Hello"));
        assertEquals(slist.get(0), "Hello");

        TestButton button = new TestButton("test");
        assertEquals(button.getLabel(), "test");
        button.updateLabel("newtest");
        assertEquals(button.getLabel(), "newtest");
    }
}
