package alloverdev.maps;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class Game extends JPanel {

    private TileMap mappa;
    private String[][] mappa_calpestabile;
    private int tileWidth, tileHeight;
    private String[][] tileNumber; //questa matrice contiene il numero delle tile, servirà per recuperare il circondario dell'agente 
    private int dim_circondario;
    private ArrayList<int[]> caselle_spawn; //arraylist contenente le caselle in cui gli agenti possono spawnare

    //costruttore
    public Game(String filename, String filename1, int circondario) {
        caselle_spawn = new ArrayList<>();
        dim_circondario = circondario;
        try {
            this.mappa = loadMap(filename, filename1);
        } catch (IOException ex) {
            Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    //metodo di lettura mappa da file; il file deve contenere una matrice di numeri separati da virgola;
    //ogni numero corrisponde all'immagine di un tile
    public TileMap loadMap(String filename, String filename1) throws IOException {

        ArrayList lines = new ArrayList();
        ArrayList lines1 = new ArrayList();
        int width = 0;
        int height = 0;
        // leggo ogni riga del file       
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        BufferedReader reader1 = new BufferedReader(new FileReader(filename1));
        while (true) {
            String line = reader.readLine();
            String line1 = reader1.readLine();
            // se non ci sono più linee
            if (line == null) {
                reader.close();
                break;
            }
            if (line1 == null) {
                reader1.close();
                break;
            }
            // aggiungo ogni linea del file all'arraylist, eccetto i commenti
            if (!line.startsWith("#")) {
                lines.add(line);
            }
            if (!line1.startsWith("#")) {
                lines1.add(line1);
            }
        }
        // definizione numero di tile in orizzontale e verticale (dimensioni)
        height = lines.size();
        for (int i = 0; i < height; i++) {
            String line_for_size = (String) lines.get(i);
            String[] ch_for_size = line_for_size.split(",");
            width = Math.max(width, ch_for_size.length);
        }
        this.tileNumber = new String[width + 2 * (dim_circondario / 2)][height + 2 * (dim_circondario / 2)]; //aggiungo righe e colonne pari a quante ne sarebbero visibili oltre la mappa in caso di agente sul bordo
        this.mappa_calpestabile = new String[width + 2 * (dim_circondario / 2)][height + 2 * (dim_circondario / 2)];               //       
        for (int i = 0; i < height + 2 * (dim_circondario / 2); i++) {
            for (int j = 0; j < width + 2 * (dim_circondario / 2); j++) {
                this.tileNumber[j][i] = "0"; //inizializzo tutte le tile a quella nulla codificata con zero (tile tutta nera)
                this.mappa_calpestabile[j][i] = "0";
            }
        }
        //creo la TileMap
        TileMap newMap = new TileMap(width, height);
        Image img = null;
        //aggiunta di ogni immagine al tile corrispondente secondo la codifica
        for (int y = 0; y < height; y++) {
            String line = (String) lines.get(y);
            String line1 = (String) lines1.get(y);
            String[] ch = line.split(",");
            String[] ch1 = line1.split(",");
            for (int x = 0; x < ch.length; x++) {
                img = alloverdev.Assets.bufferedImage("Tiles/" + ch[x] + ".jpg");
                newMap.setTile(x, y, img);
                this.tileNumber[x + dim_circondario / 2][y + dim_circondario / 2] = ch[x];
                this.mappa_calpestabile[x + dim_circondario / 2][y + dim_circondario / 2] = ch1[x];
                if (ch1[x].equals("2")) {
                    int[] caselle = new int[2];
                    caselle[0] = y;
                    caselle[1] = x;
                    caselle_spawn.add(caselle);
                }
            }
        }

        this.tileWidth = img.getWidth(null);
        this.tileHeight = img.getHeight(null);
        return newMap;
    }

    //disegno la mappa letta
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        // The map is repainted on every agent move: compose it once into an
        // image and rebuild it only when a tile changes
        TileMap m = this.getMappa();
        if (cache == null || cachedMap != m || cachedVersion != m.getVersion()) {
            cachedMap = m;
            cachedVersion = m.getVersion();
            cache = new java.awt.image.BufferedImage(m.getWidth() * tileWidth, m.getHeight() * tileHeight,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D c = cache.createGraphics();
            int posx = 0;
            int posy = 0;
            for (int i = 0; i < m.getHeight(); i++) {
                for (int j = 0; j < m.getWidth(); j++) {
                    c.drawImage(m.getTile(j, i), posx, posy, null);
                    posx += this.tileWidth;
                }
                posx = 0;
                posy += this.tileHeight;
            }
            c.dispose();
        }
        g.drawImage(cache, 0, 0, null);
    }

    private java.awt.image.BufferedImage cache;
    private TileMap cachedMap;
    private int cachedVersion;

    public TileMap getMappa() {
        return mappa;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public String[][] getTileNumber() {
        return tileNumber;
    }

    public int getDim_circondario() {
        return dim_circondario;
    }

    public String[][] getMappa_calpestabile() {
        return mappa_calpestabile;
    }

    public void setMappa_calpestabile(String valore, int i, int j) {
        this.mappa_calpestabile[i][j] = valore;
    }

    public ArrayList<int[]> getCaselle_spawn() {
        return caselle_spawn;
    }

    public String getTilePos(String s) {
        String result = "";
        String[][] TileNumber = getTileNumber();
        for (int i = 0; i < TileNumber.length; i++) {
            for (int j = 0; j < TileNumber[i].length; j++) {
                if (TileNumber[i][j].equals(s)) {
//                    System.out.println("Tile" + s + " found in pos" + i+"," + j);
                    result = result + i + "," + j + "-";
                }
            }
        }
        return result;
    }

    public int[][] getNotWalkableTiles() {
        String result = "";
        String[][] Map = getMappa_calpestabile();

        for (int i = 0; i < Map[i].length; i++) { //0-34
            for (int j = 0; j < Map.length; j++) { //0-52
                if (!Map[j][i].equals("1") && !Map[j][i].equals("2")) {
                    //System.out.println("Not Walkable Tile found in pos x=" + j + ", y=" +i);
                    result = result + j + "," + i + "-";
                }
            }
        }
        String[] arr = result.split("-");
        int size = arr.length;
        int[][] intArr = new int[size][2];

        for (int i = 0; i < size; i++) {

            String[] coord = arr[i].split(",");

            intArr[i][1] = Integer.parseInt(coord[0]); //X
            intArr[i][0] = Integer.parseInt(coord[1]); //Y
        }

        return intArr;
    }

    public void setDim_circondario(int dim_circondario) {
        this.dim_circondario = dim_circondario;
    }

}
