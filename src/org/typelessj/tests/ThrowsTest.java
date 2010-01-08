//
// $Id$

package org.typelessj.tests;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests checked exception throwing and catching.
 */
public class ThrowsTest
{
    @Test public void testThrows () throws IOException
    {
        try {
            thrower();
        } catch (IOException ioe) {
            return;
        } catch (NullPointerException npe) {
            return;
        }
        fail("Should not be reached.");
    }

    protected void thrower () throws IOException
    {
        throw new IOException("Oh noez!");
    }
}
