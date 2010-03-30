//
// $Id$

package org.ductilej.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests serializing and unserializing detyped objects.
 */
public class SerializeTest
{
    public static class Person implements Serializable
    {
        public final String firstName;
        public final String lastName;
        public final int age;

        public Person (String firstName, String lastName, int age)
        {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

        public boolean equals (Object other)
        {
            Person op = (Person)other;
            return firstName.equals(op.firstName) &&
                lastName.equals(op.lastName) && age == op.age;
        }
    }

    @Test public void testSerialize ()
        throws IOException, ClassNotFoundException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        Person p1 = new Person("Michael", "Jackson", 50);
        Person p2 = new Person("Elvis", "Presley", 42);
        oout.writeObject(p1);
        oout.writeObject(p2);
        oout.close();

        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream oin = new ObjectInputStream(bin);
        Person rp1 = (Person)oin.readObject();
        Person rp2 = (Person)oin.readObject();
        oin.close();

        assertEquals(p1, rp1);
        assertEquals(p2, rp2);
    }
}
