//
// $Id$

package OtherPackage;

import org.junit.Test;

public final class ConstructorPackageTest {

    private static final class Tester {
    }

    public ConstructorPackageTest() {
    }

    @Test
    public void testConstructor() {
        // The following line results in the compiler error "constructor
        // ConstructorPackageTestClass in class ConstructorPackageTestClass
        // cannot be applied to given types".
        new org.ductilej.tests.helper.ConstructorPackageTestClass(0);
    }
}

