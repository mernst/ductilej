//
// $Id$

// This package name "StaticMethodResolutionTestPackage" must be identical to the simple name of
// the class defined in the source file StaticMethodResolutionTestPackage.java.
package StaticMethodResolutionTestPackage;

import org.junit.Test;

// Import is required to demonstrate bug: importing a class with the same name
// as the current package breaks static method resolution at compile time
// presumably due to name ambiguities which Ductile attempts to resolve
// differently from regular Java compilation.
import org.ductilej.tests.helper.StaticMethodResolutionTestPackage;

/**
 * Tests that static methods are correctly resolved when a class with the same
 * simple name as the current package is imported.
 */
public class StaticMethodResolutionTest
{
    public static void someStaticMethod () {
        // noop
    }

    @Test public void testDummy () {
        // Ductile generates erroneous "cannot find symbol" on the following line.
        someStaticMethod();
    }
}
