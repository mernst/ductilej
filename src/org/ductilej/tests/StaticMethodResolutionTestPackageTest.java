//
// $Id$

package OtherPackage;

import org.junit.Test;

/**
 * Tests that static methods are correctly resolved when a class with the same
 * simple name as the current package is imported. The simple name of this class
 * "StaticMethodResolutionTestPackageTest" must be identical to the name of the
 * package containing "StaticMethodResolutionTest".
 */
public class StaticMethodResolutionTestPackageTest {

    public StaticMethodResolutionTestPackageTest() {
    }

    @Test
    public void testDummy() {
    }
}

