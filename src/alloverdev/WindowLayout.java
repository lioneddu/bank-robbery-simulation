package alloverdev;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Places the windows on screen now that every agent runs in the same process:
 * the main map in the top-left corner and the agents' mini GUIs tiled in the
 * free space on its right (or below it, on small screens).
 */
public final class WindowLayout {

    private static final int GAP = 4;

    private static Rectangle mainBounds = new Rectangle(0, 0, 0, 0);
    private static int nextX = -1;
    private static int nextY = -1;
    private static int columnWidth = 0;

    private WindowLayout() {
    }

    private static Rectangle screen() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }

    public static synchronized Point mainWindow(Dimension size) {
        Rectangle s = screen();
        mainBounds = new Rectangle(s.x, s.y, size.width, size.height);
        nextX = -1;
        return mainBounds.getLocation();
    }

    public static synchronized Point nextAgentWindow(Dimension size) {
        Rectangle s = screen();
        boolean roomOnRight = s.x + s.width - (mainBounds.x + mainBounds.width) >= size.width;
        int startX = roomOnRight ? mainBounds.x + mainBounds.width + GAP : s.x;
        int startY = roomOnRight ? s.y : mainBounds.y + mainBounds.height + GAP;

        if (nextX < 0) {
            nextX = startX;
            nextY = startY;
            columnWidth = 0;
        }
        if (nextY + size.height > s.y + s.height) {
            // Column full: start a new one (wrap to the beginning if out of screen)
            nextX += columnWidth + GAP;
            nextY = startY;
            columnWidth = 0;
            if (nextX + size.width > s.x + s.width) {
                nextX = startX;
            }
        }
        Point p = new Point(nextX, nextY);
        nextY += size.height + GAP;
        columnWidth = Math.max(columnWidth, size.width);
        return p;
    }
}
