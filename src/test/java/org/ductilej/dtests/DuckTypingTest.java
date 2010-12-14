//
// $Id$

package org.ductilej.dtests;

import java.util.Collections;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests proxying of library interfaces to classes that don't declare that they implement those
 * interfaces but do implement the necessary methods.
 */
public class DuckTypingTest
{
    public class Adder {
        public int value;

        public boolean add (Integer value) {
            this.value += value;
            return true;
        }
    }

    @Test public void testAddAll () {
        Adder a = new Adder();
        Collections.addAll(a, 1, 2, 3, 4, 5);
        assertEquals(15, a.value);
    }

    @Test(expected=NoSuchMethodError.class)
    public void testMissingMethods () {
        Adder a = new Adder();
        Collections.max(a); // will throw NSME when looking for iterator()
    }
}
