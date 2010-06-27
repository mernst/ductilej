//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

class Bar {
}

class Foo {
    public Bar firstMethod() {return null;}
}

/**
 * Various constructs that, while not constituting legal Java code, ought
 * to compile successfully under DuctileJ.
 */
public final class ShouldCompileUnderDuctileJTest {

    public ShouldCompileUnderDuctileJTest() {
    }

    /**
     * Bar does not define a method "secondMethod": however, DuctileJ should
     * be able to compile this code and it ought to result in a runtime error
     * instead of a compile error.
     */
    @Test public void testUndefinedMethod() {
        Foo f = new Foo();

        // Even though secondMethod does not exist on Bar this code should
        // compile under DuctileJ.
        f.firstMethod().secondMethod();
    }
}

