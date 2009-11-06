//
// $Id$

package org.typelessj.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests array store.
 */
public class ArrayStoreTest
{
    @Test public void testArrayStore ()
    {
        String[] test1 = new String[] { "zero", "one", "two" };
        System.out.println("One: " + test1[0] + " " + test1[1] + " " + test1[2]);
        String[] test2 = new String[3];
        System.out.println("Two: " + test2[0] + " " + test2[1] + " " + test2[2]);
        test2[0] = "0";
        test2[1] = "1";
        test2[2] = "2";
        System.out.println("New two: " + test2[0] + " " + test2[1] + " " + test2[2]);
    }
}
