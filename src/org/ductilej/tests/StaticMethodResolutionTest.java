//
// $Id$

// This package name "StaticMethodResolutionTestPackageTest" must be identical to the
// simple name of the class defined in the source file
// StaticMethodResolutionTestPackageTest.java.
package StaticMethodResolutionTestPackageTest;

import org.junit.Test;

// Import is required to demonstrate bug: importing a class with the same name
// as the current package breaks static method resolution at compile time
// presumably due to name ambiguities which Ductile attempts to resolve
// differently from regular Java compilation.
import OtherPackage.StaticMethodResolutionTestPackageTest;

/**
 * Tests that static methods are correctly resolved when a class with the same
 * simple name as the current package is imported.
 */
public class StaticMethodResolutionTest {

    public static void main(String[] args) {
        someStaticMethod();
    }

    public static void someStaticMethod() {
    }

    public StaticMethodResolutionTest() {
    }

    @Test
    public void testDummy() {
    }
}

