//
// $Id$

package org.ductilej.dtests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Should compile under DuctileJ but not javac due to lack of check
 * exception annotation ("throws") on Foo's constructor.
 */
public class UncheckedExceptionTest
{
    class Foo {
        public Foo() {
            throw new java.lang.Exception();
        }
    }

    public UncheckedExceptionTest() {
    }
}

