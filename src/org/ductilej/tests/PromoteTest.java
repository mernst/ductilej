//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests fiddly bits related to primitive type promotion.
 */
public class PromoteTest
{
    @Test public void testPromote ()
    {
        // ensure that during detyping the type of _nextInternCode++ resolves to short not int, and
        // ensures that at runtime we also don't inadvertently promote the short to an int
        short value = createInternMapping(_nextInternCode++);
        assertEquals(1, value);
    }

    protected Short createInternMapping (short code)
    {
        return code;
    }

    // we're also testing the implicit narrowing that takes place during this initializer
    // assignment (1 is an integer literal and it is narrowed to a short on assignment)
    protected short _nextInternCode = 1;
}
