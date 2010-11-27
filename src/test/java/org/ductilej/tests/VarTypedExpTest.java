//
// $Id$

package org.ductilej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

public class VarTypedExpTest
{
    public class Foo {
    }

    public class Bar<T extends Runnable> extends Foo
    {
        public Bar (Class<T> clazz) {
        }
    }

    @Test public void testInstantiate ()
    {
        // we're going to resolve the type of this constructor and then insert type carrying
        // arguments into the AST; this tests that the necessary constructor resolution properly
        // instantiates type variables, so we get "new Bar<Runnable>" rather than "new Bar<T>"
        foo(new Bar<Runnable>(Runnable.class) {
            // magical inner class juice!
        });
    }

    protected void foo (Foo foo) {
    }
}
