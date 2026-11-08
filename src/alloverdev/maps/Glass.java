package alloverdev.maps;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

public class Glass extends JPanel {

    private HashMap<String, Image> agenti; //chiave: id agente, valore: immagine corrispondente
    private HashMap<String, int[]> posizioni; //chiave: id agente, valore: la sua posizione
    private ArrayList<int[]> caselle_spawn; //caselle di spawn
    private ArrayList<Boolean> spawn_occupati; //booleano per indicare se le caselle di spawn sono occupate o no
    private String[][] mappa_calpestabile;

    public Glass() {
        agenti = new HashMap<>();
        posizioni = new HashMap<>();
        this.setOpaque(false);
        caselle_spawn = new ArrayList<>();
        spawn_occupati = new ArrayList<>();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        doDrawing(g);

        Toolkit.getDefaultToolkit().sync();
    }

    public void doDrawing(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        for (String s : agenti.keySet()) {
            g2d.drawImage(agenti.get(s), posizioni.get(s)[0], posizioni.get(s)[1], this);
        }
    }

    public int[] addAgent(String s1, String s2) {
        //genero numero random nel range del numero di posti disponibili per lo spawn e seleziono lo spawn associato
        Random rand = new Random();
        int spawn_casuale = rand.nextInt(caselle_spawn.size());

        while (spawn_occupati.get(spawn_casuale)) {
            spawn_casuale = rand.nextInt(caselle_spawn.size());
        }
        int x_spawn = caselle_spawn.get(spawn_casuale)[0];
        int y_spawn = caselle_spawn.get(spawn_casuale)[1];

        spawn_occupati.set(spawn_casuale, Boolean.TRUE);

        int[] posizione = {y_spawn * 24, x_spawn * 24};
//        int[] posizione; 
////        if (s2.equals("CLN"))
//        posizione = new int[]{27 * 24, 16 * 24}; 
////        else
////        posizione = new int[]{24 * 24, 4* 24}; 

        System.out.println("addAgent" + posizione[0] + "," + posizione[1]);
        posizioni.put(s1, posizione);
        switch (s2) {
            case "THF":
                agenti.put(s1, alloverdev.Assets.image("Immagini_agenti/LadroPNG.png"));
                break;
            case "CLN":
                agenti.put(s1, alloverdev.Assets.image("Immagini_agenti/ClientePNG.png"));
                break;
            case "EMP":
                agenti.put(s1, alloverdev.Assets.image("Immagini_agenti/ImpiegatoPNG.png"));
                break;
            case "GRD":
                agenti.put(s1, alloverdev.Assets.image("Immagini_agenti/GuardiaPNG.png"));
                break;

        }
        this.repaint();

        return posizione;
    }

    /*
    Metodo per la rimozione dell'agente a seguito della chiusura della finestra del circondario
    Rimuove l'agente corrispondente dai vettori che tengono traccia delle posizioni e dell'immagine
    @param idAgent stringa che rappresenta username dell'agente
     */
    public void removeAgent(String idAgent) {

        posizioni.remove(idAgent);
        agenti.remove(idAgent);

        this.repaint();

    }

    public HashMap<String, int[]> getPosizioni() {
        return this.posizioni;
    }

    public synchronized int[] setPosizione(String nick, int[] pos) {
//        System.out.println("setPosizione - nick" + nick);
//        System.out.println("setPosizione - pos" + pos[0] + "-" + pos[1]);
        if (this.posizioni.containsKey(nick)) {
            this.posizioni.put(nick, pos);

            this.repaint();
        }
        return pos;
    }

    public int[] getPosizione(String nick) {
        return this.posizioni.get(nick);
    }

    //metodo per settare le caselle di spawn ricevute dalla mappa
    public void setCaselle_spawn(ArrayList<int[]> caselle_spawn) {
        this.caselle_spawn = caselle_spawn;
        for (int i = 0; i < this.caselle_spawn.size(); i++) {
            this.spawn_occupati.add(Boolean.FALSE);
        }
    }

    public void setMappa_calpestabile(String[][] mappa_calpestabile, int width, int height) {
        this.mappa_calpestabile = new String[width][height];
        this.mappa_calpestabile = mappa_calpestabile;
    }

    //metodo per settare le caselle di spawn a occupate o meno
    public void setSpawn_occupati(int i, Boolean b) {
        this.spawn_occupati.set(i, b);
    }

    public void setImageAgent(String s, Image img) {
        this.agenti.put(s, img);
        this.repaint();
    }

}
