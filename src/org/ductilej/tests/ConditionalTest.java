//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of various conditional statements.
 */
public class ConditionalTest
{
    @Test public void whileTest ()
    {
        int ii = 0;
        while (ii < 5) {
            ii += 1;
        }
        assertEquals(ii, 5);
    }

    @Test public void doTest ()
    {
        int ii = 0;
        do {
            ii += 1;
        } while (ii < 5);
        assertEquals(ii, 5);
    }

    @Test public void ifTest ()
    {
        int ii = 0;
        if (ii < 0) {
            ii = 5;
        }
        assertEquals(ii, 0);
    }

    @Test public void forTest ()
    {
        int count = 0;
        for (int ii = 0; ii < 5; ii++) {
            count = count + 1;
        }
        assertEquals(count, 5);
    }

    @Test public void condExprTypeTest ()
    {
        // ensure that resolveType() on a conditional expression with a null in one or the other
        // position properly returns the type of the non-null expression
        int a = 0;
        foo(a < 2 ? "bar" : null);
        foo(a < 2 ? null : "bar");
    }

    protected void foo (String value)
    {
    }

    protected void foo (Integer value)
    {
    }
}
