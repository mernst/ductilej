//
// $Id$

package MyInterfaceTest;

import org.junit.Test;

import org.ductilej.tests.helper.one.*;
import org.ductilej.tests.helper.two.*;

/**
 * Demonstrates similar compiler bug as StaticMethodResolutionTest.java: this
 * time the ambiguity results from the two identically named interfaces (from
 * different packages) being imported.
 */
public class StaticMethodAmbiguousInterfaceTest
    implements org.ductilej.tests.helper.one.MyInterfaceTest
{
    @Test public void testInvokeFoo () {
        // The following line results in the following compiler error:
        // "reference to MyInterfaceTest is ambiguous, both interface
        // org.ductilej.tests.helper.two.MyInterfaceTest in
        // org.ductilej.tests.helper.two and interface
        // org.ductilej.tests.helper.one.MyInterfaceTest in
        // org.ductilej.tests.helper.one match".
        foo();
    }

    private static void foo () {
    }
}
