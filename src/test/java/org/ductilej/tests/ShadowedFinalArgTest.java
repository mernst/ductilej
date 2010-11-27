//
// $Id$

package org.ductilej.tests;

import javax.swing.JLabel;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests handling of shadowed final arguments.
 */
public class ShadowedFinalArgTest
{
    public static class MyLabel extends JLabel {
        @Override public void setText (final String text) {
            super.setText(text);
            new Runnable() {
                public void run () {
                    String val = text;
                }
            }.run();
        }
    }

    @Test public void testShadowedFinal ()
    {
        // we have nothing to test here, if the above code compiles, then we're fine
    }
}
