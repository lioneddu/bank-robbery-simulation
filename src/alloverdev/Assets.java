package alloverdev;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Access to the files in the {@code work/} folder (maps and images).
 *
 * <p>The base folder is set once by the launcher, so the simulation works from
 * any working directory and in the browser (where files are served under
 * {@code /app/...}). Images are loaded synchronously and cached, because the
 * GUIs repaint the same tiles very often.</p>
 */
public final class Assets {

    private static volatile File base = new File(".");
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private Assets() {
    }

    public static void setBase(File dir) {
        base = dir;
        CACHE.clear();
    }

    public static File getBase() {
        return base;
    }

    private static String normalize(String relative) {
        return relative.startsWith("./") ? relative.substring(2) : relative;
    }

    /**
     * @return the path of a file inside the base folder.
     */
    public static String path(String relative) {
        return new File(base, normalize(relative)).getPath();
    }

    /**
     * @return the (cached) image, read synchronously.
     */
    public static BufferedImage bufferedImage(String relative) throws IOException {
        String key = normalize(relative);
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = ImageIO.read(new File(base, key));
            if (img == null) {
                throw new IOException("Unreadable image: " + key);
            }
            CACHE.put(key, img);
        }
        return img;
    }

    /**
     * Same as {@link #bufferedImage(String)} but returns an empty image
     * instead of failing, for the places where the original code used
     * {@code new ImageIcon(path).getImage()}.
     */
    public static BufferedImage image(String relative) {
        try {
            return bufferedImage(relative);
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
    }
}
