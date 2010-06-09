//
// $Id$

// Demonstrates similar compiler bug as StaticMethodResolutionTest.java: this
// time the ambiguity results from the two identically named interfaces (from
// different packages) being imported.

package MyInterfaceTest;

import org.junit.Test;
import StaticMethodAmbiguousInterfaceTestPackage1.*;
import StaticMethodAmbiguousInterfaceTestPackage2.*;

public class StaticMethodAmbiguousInterfaceTest implements StaticMethodAmbiguousInterfaceTestPackage1.MyInterfaceTest {

    public StaticMethodAmbiguousInterfaceTest() {
    }

    @Test
    public void testInvokeFoo() {
        // The following line results in the following compiler error:
        // "reference to MyInterfaceTest is ambiguous, both interface
        // StaticMethodAmbiguousInterfaceTestPackage2.MyInterfaceTest in
        // StaticMethodAmbiguousInterfaceTestPackage2 and interface
        // StaticMethodAmbiguousInterfaceTestPackage1.MyInterfaceTest in
        // StaticMethodAmbiguousInterfaceTestPackage1 match".
        foo();
    }

    private static void foo() {
    }
}

