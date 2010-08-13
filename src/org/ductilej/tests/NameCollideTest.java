//
// $Id$

package org.ductilej.tests;

import java.awt.event.*;
import javax.swing.JButton;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests some annoying name collision behavior caused by TreeMaker.Type().
 */
public class NameCollideTest
{
    public static class MouseListener extends MouseAdapter {
        public void mouseClicked (MouseEvent e) { /* yay! */ }
    }

    @Test public void testShadowedName () {
        JButton button = new JButton();
        // ensure that this gets transformed into:
        //  RT.invoke("addMouseListener", new Class<?>{ java.awt.event.MouseListener.class }, ...)
        // rather than
        //  RT.invoke("addMouseListener", new Class<?>{ MouseListener.class }, ...)
        // which will cause badness due to the shadowing MouseListener above.
        button.addMouseListener(new MouseListener());
        assertTrue(1 == 1);
    }
}
