//
// $Id$

package org.ductilej.tests;

import java.awt.Component;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Does something extraordinary.
 */
public class LibraryFieldAssignTest
{
    public static class TestRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent (
            JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row,
            boolean hasFocus) {
            // assigning to a field in our superclass which will not have been detyped
            selected = sel;
            // also assigning to field in super class, this time via explicit this
            this.hasFocus = hasFocus;
            return null;
        }

        public boolean getSelected () {
            return selected;
        }
    }

    @Test public void testFieldAssign ()
    {
        TestRenderer t = new TestRenderer();
        t.getTreeCellRendererComponent(null, null, true, true, true, 0, true);
        assertEquals(true, t.getSelected());
    }
}
