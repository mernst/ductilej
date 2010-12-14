//
// $Id$

package org.ductilej.dtests;

/*
 * Demonstrates an uncaught exception in Detyper.
 * Compilation with Detyper leads to a call stack like the following:
 *  !!! Symbol resolution failed [expr=Foo, sym=symbol not found error]
 *  !!! Unable to resolve type [expr=Foo, pkind=2]
 *  !!! Symbol resolution failed [expr=Foo, sym=symbol not found error]
 *  !!! Unable to resolve type [expr=Foo, pkind=2]
 *  !!! Fatal error [file=RegularFileObject[/home/rcook/src/ductilej/src/org/ductilej/dtests/NullPointerExceptionTest.java]]
 *  
 *
 *  An annotation processor threw an uncaught exception.
 *  Consult the following stack trace for details.
 *  java.lang.NullPointerException
 *  	at com.sun.tools.javac.code.Types$DefaultTypeVisitor.visit(Types.java:3404)
 *  	at com.sun.tools.javac.code.Types.isSameType(Types.java:575)
 *  	at org.ductilej.detyper.Detype.isConstDecl(Detype.java:1173)
 *  	at org.ductilej.detyper.Detype.visitVarDef(Detype.java:345)
 *  	at com.sun.tools.javac.tree.JCTree$JCVariableDecl.accept(JCTree.java:712)
 *  	at com.sun.tools.javac.tree.TreeTranslator.translate(TreeTranslator.java:58)
 *  	at com.sun.tools.javac.tree.TreeTranslator.translate(TreeTranslator.java:70)
 *  	at org.ductilej.util.PathedTreeTranslator.visitBlock(PathedTreeTranslator.java:105)
 *  	at org.ductilej.detyper.Detype.visitBlock(Detype.java:1098)
 */
public final class NullPointerExceptionTest {

    public NullPointerExceptionTest() {
        Foo[] foos;
    }
}

