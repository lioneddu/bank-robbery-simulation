import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Re-encodes every image of a folder as PNG, keeping the original file name.
 *
 * <p>Used only for the web version: in the browser (CheerpJ) the JPEG decoder of
 * ImageIO relies on native code that is not available, while PNG decoding is
 * pure Java. ImageIO detects the format from the content, so a PNG saved as
 * {@code 65.jpg} is read correctly and the code needs no change.</p>
 *
 * <p>Usage: {@code java tools/ConvertTiles.java <source dir> <target dir>}</p>
 */
public class ConvertTiles {

    public static void main(String[] args) throws IOException {
        File src = new File(args[0]);
        File dst = new File(args[1]);
        dst.mkdirs();
        int n = 0;
        File[] files = src.listFiles();
        if (files == null) {
            throw new IOException("Folder not found: " + src);
        }
        for (File f : files) {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                throw new IOException("Unreadable image: " + f);
            }
            // Normalise to ARGB: some tiles are greyscale JPEGs
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            out.createGraphics().drawImage(img, 0, 0, null);
            ImageIO.write(out, "png", new File(dst, f.getName()));
            n++;
        }
        System.out.println("Converted " + n + " images into " + dst);
    }
}
