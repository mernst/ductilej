//
// $Id$

package org.ductilej.tests;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Date;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests untyping of method defs and applications.
 */
public class DefApplyTest
{
    public class Person {
        public Person (String name, int age) {
            _name = name;
            _age = age;
        }

        public String getName () {
            return _name;
        }

        public int getAge () {
            return _age;
        }

        public Person reproduce (String name) {
            return new Person(name, 0);
        }

        protected String _name;
        protected int _age;
    }

    public class DatePerson extends Person {
        public DatePerson (int age) {
            // ensures that RT.newInstance(Date.class, ...) detects that the arguments to super()
            // are in a "static" context (i.e. they cannot reference "this")
            super(new Date().toString(), age);
        }
    }

    public class MyPanel extends JPanel
    {
        public MyPanel () {
            setLayout(new BorderLayout());
            JLabel label = new JLabel("Testing");
            add(label, BorderLayout.CENTER);
        }

        public Dimension getPreferredSize () {
            Dimension d = super.getPreferredSize();
            d.height = 50;
            return d;
        }
    }

    @Test public void testDefApply ()
    {
        Person p = new Person("Theodor Geisel", 62);
        assertEquals(p.getName(), "Theodor Geisel");
        assertEquals(p.getAge(), 62);
        Person p2 = p.reproduce("Sneetch");
        assertEquals(p2.getName(), "Sneetch");
        assertEquals(p2.getAge(), 0);
    }
}
