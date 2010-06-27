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
    public static class MyString {
        public final String value;
        public MyString (String value) {
            this.value = value;
        }
    }

    public static class Person implements Serializable
    {
        public MyString firstName;
        public MyString lastName;
        public int age;

        public Person (String firstName, String lastName, int age)
        {
            this.firstName = new MyString(firstName);
            this.lastName = new MyString(lastName);
            this.age = age;
        }

        public boolean equals (Object other)
        {
            Person op = (Person)other;
            return firstName.value.equals(op.firstName.value) &&
                lastName.value.equals(op.lastName.value) && age == op.age;
        }

        private void readObject (ObjectInputStream in)
            throws IOException, ClassNotFoundException
        {
            firstName = new MyString(in.readUTF());
            lastName = new MyString(in.readUTF());
            age = in.readInt();
        }

        private void writeObject (ObjectOutputStream out)
            throws IOException
        {
            out.writeUTF(firstName.value);
            out.writeUTF(lastName.value);
            out.writeInt(age);
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
