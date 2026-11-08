package alloverdev.maps;

import java.awt.Image;

public class TileMap {

    private Image[][] tiles;

    public TileMap(int width, int height) {
        this.tiles = new Image[width][height];
    }

    /**
     * larghezza della mappa
     */
    public int getWidth() {
        return tiles.length;
    }

    /**
     * altezza della mappa
     */
    public int getHeight() {
        return tiles[0].length;
    }

    /**
     * getter dei tile in posizione x,y (coordinate)
     *
     * @param x coordinata tile x
     * @param y coordinata tile y
     */
    public Image getTile(int x, int y) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
            return null;
        } else {
            return tiles[x][y];
        }
    }

    /**
     * setter dei tile in posizione x,y
     *
     * @param x coordinata x
     * @param y coordinata y
     * @param tile immagine tile
     */
    public void setTile(int x, int y, Image tile) {
        tiles[x][y] = tile;
        version++;
    }

    private volatile int version;

    /**
     * Change counter: lets whoever draws the map keep a cached copy and
     * rebuild it only when a tile changes.
     */
    public int getVersion() {
        return version;
    }

}
