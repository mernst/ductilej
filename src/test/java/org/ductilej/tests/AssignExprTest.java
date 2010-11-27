//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssignExprTest
{
    @Test public void testAssignExpr ()
    {
        int value = 5;
        // we're mainly testing that 'msg' is registered in the symbol table at the time that we
        // process msg's initializer expression
        String msg = (value == 5) ? msg = "Redundant" : null;
        assertEquals("Redundant", msg);
    }
}
