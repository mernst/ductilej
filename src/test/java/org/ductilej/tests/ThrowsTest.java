//
// $Id$

package org.ductilej.tests;

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
        } catch (java.io.FileNotFoundException fnfe) {
            return;
        } catch (IOException ioe) {
            // test detyping of vars inside catch block; we detype inside, we don't detype vars in
            // the actual catch clause (ioe above)
            int pos = ioe.getMessage().indexOf('.');
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

    protected void castingThrower () throws IOException
    {
        Throwable ioe = new IOException();
        throw (IOException) ioe;
    }

    protected void nonCastingThrower () throws IOException
    {
        IOException ioe = new IOException();
        throw ioe;
    }

    protected void callingThrower () throws IOException
    {
        throw error();
    }

    protected static IOException error ()
    {
        return new IOException();
    }
}
