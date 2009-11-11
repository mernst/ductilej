//
// $Id$

package org.typelessj.tests;

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
