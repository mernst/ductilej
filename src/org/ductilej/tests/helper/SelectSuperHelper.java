//
// $Id$

package org.ductilej.tests.helper;

/**
 * Defines a class outside the tests package for testing cross package bits.
 */
public class SelectSuperHelper
{
    public static class A {
        protected A (String foo) {
            _foo = foo;
        }
        protected String getFoo () {
            return _foo;
        }
        protected String _foo;
    }
}
