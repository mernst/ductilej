import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class bggen
{
    public static final int WIDTH = 824;
    public static final int ROW_HEIGHT = 25;
    public static final int ROWS = 24;
    public static final int GAP = 4;

    public static void main (String[] args)
        throws Exception
    {
        BufferedImage hole = ImageIO.read(new File("hole.png"));

        BufferedImage image = new BufferedImage(
            WIDTH+2*hole.getWidth(), ROW_HEIGHT*ROWS+2*GAP, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gfx = image.createGraphics();
        gfx.setColor(new Color(0xD0, 0xF0, 0xD0, 0x99));
        for (int rr = 0; rr < ROWS; rr += 6) {
            gfx.fillRect(hole.getWidth(), GAP+rr*ROW_HEIGHT, WIDTH, 3*ROW_HEIGHT);
        }
        for (int rr = 0; rr < ROWS; rr += 2) {
            gfx.drawImage(hole, 0, GAP+rr*ROW_HEIGHT, null);
            gfx.drawImage(hole, WIDTH+hole.getWidth(), GAP+rr*ROW_HEIGHT, null);
        }
        gfx.dispose();
        ImageIO.write(image, "png", new File("background.png"));
    }
}
