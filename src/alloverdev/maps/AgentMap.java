package alloverdev.maps;

import alloverdev.agents.Agent;
import alloverdev.interfaces.AgentsIF;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class AgentMap extends JPanel {

//    private Glass glass;
    private Game g;
    private AgentsIF agent;
    private String type;
    private String logo;
    private TileMap mappa;
    private String[][] mappa_calpestabile;
    private int tileHeight = 24;
    private int tileWidth = 24;
    private int circondario;
    private boolean creato_frame = false;
    private JTextArea txt;

    public AgentMap(AgentsIF agent) {
        this.agent = agent;
        this.type = "vuoto";
        this.mappa_calpestabile = null;
        this.txt = new JTextArea();
    }

    public AgentMap() {
        this.type = "vuoto";
        this.mappa_calpestabile = null;
        this.txt = new JTextArea();
    }

    private void initComponents() {

        Agent ag = (Agent) this.agent;

        JFrame frame = new JFrame(ag.getConn().getLogin());
        ag.getConn().registerWindow(frame);
        JScrollPane sp = new JScrollPane();
        txt.setText(statsAgent(ag));
        txt.setEditable(false);
        frame.setVisible(true); //Istruzione spostata prima del set size in modo che poi i metodi getInsets possano restituire i pixel da rimuovere

        //SetSIze = (NumColonneMappa*NumTIleLarg) , (NumRigheMappa*NumTileAltezza - TitleBarHeight)
        frame.setSize(2 * circondario * tileWidth + frame.getInsets().right + frame.getInsets().left, circondario * tileHeight + frame.getInsets().top + frame.getInsets().bottom);

        frame.setLayout(new GridLayout(1, 2));

        //Listener su chiusura finestra che esegue end session e deregistra l'agente
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent evt) {
                endSession(evt);
            }
        });

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(alloverdev.WindowLayout.nextAgentWindow(frame.getSize()));
        frame.setResizable(false);
        this.setBackground(Color.WHITE);
        frame.add(this);
        //frame.add(panel_soldo, BorderLayout.EAST);

        txt.setForeground(Color.blue);
        txt.setBackground(Color.LIGHT_GRAY);

        sp.setViewportView(txt);
        frame.add(sp);

//        initGlassPane(frame);
        updateGui(frame);

    }

    public String statsAgent(Agent ag) {

        String txt = "";

        switch (this.type) {
            case "THF":
                txt = ag.toString();
                break;
            case "CLN":
                txt = ag.toString();
                break;
            case "EMP":
                txt = ag.toString();
                break;
            case "GRD":
                txt = ag.toString();
                break;
        }

        return txt;

    }


    /*
    Metodo avviato alla chiusura della mappa con il circondario dell'agente
    Effettua la deregistrazione dell'agente e chiude il thread
     */
    private void endSession(WindowEvent evt) {
        System.out.println("End Session");
        agent.subscribe(true);
        ((Agent) agent).getConn().terminate(); // stops only this agent
    }

    public void updateGui(JFrame frame) {
        switch (type) {
            case "THF":
                logo = "./Immagini_agenti/LadroPNG.png";
                break;
            case "CLN":
                logo = "./Immagini_agenti/ClientePNG.png";
                break;
            case "EMP":
                logo = "./Immagini_agenti/ImpiegatoPNG.png";
                break;
            case "GRD":
                logo = "./Immagini_agenti/GuardiaPNG.png";
                break;
        }
        ImageIcon icon = new ImageIcon(alloverdev.Assets.image(logo));
        frame.setIconImage(icon.getImage()); // Aggiunta logo agente nella title bar
        this.repaint();
    }

    @Override
    public void paint(Graphics g) {

        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;

        int posx = 0;
        int posy = 0;
        int k = 0;
        if (!type.equals("vuoto")) {
            for (int i = 0; i < circondario; i++) {
                for (int j = 0; j < circondario; j++) {
                    g2d.drawImage(this.mappa.getTile(i, j), posx, posy, this);
                    posx += this.tileWidth;
                }
                posx = 0;
                posy += this.tileHeight;
            }
            g2d.drawImage(alloverdev.Assets.image(logo), ((circondario / 2) * tileWidth), ((circondario / 2) * tileHeight), this);
            for (int i = 0; i < circondario; i++) {
                for (int j = 0; j < circondario; j++) {
                    switch (mappa_calpestabile[j][i]) {
                        case "THF":
                            g2d.drawImage(alloverdev.Assets.image("Immagini_agenti/LadroPNG.png"), (i * tileWidth), (j * tileHeight), this);
                            break;
                        case "CLN":
                            g2d.drawImage(alloverdev.Assets.image("Immagini_agenti/ClientePNG.png"), (i * tileWidth), (j * tileHeight), this);
                            break;
                        case "EMP":
                            g2d.drawImage(alloverdev.Assets.image("Immagini_agenti/ImpiegatoPNG.png"), (i * tileWidth), (j * tileHeight), this);
                            break;
                        case "GRD":
                            g2d.drawImage(alloverdev.Assets.image("Immagini_agenti/GuardiaPNG.png"), (i * tileWidth), (j * tileHeight), this);
                            break;
                        case "GRV":
                            g2d.drawImage(alloverdev.Assets.image("Immagini_agenti/cryingThiefPNG.png"), (i * tileWidth), (j * tileHeight), this);
                            break;
                    }
                }
            }
        }

    }

//    public void initGlassPane(JFrame frame) {
//        Glass glass = new Glass();
//        frame.setGlassPane(glass);
//        glass.setVisible(true);
//        this.glass = glass;
//        System.out.println("Glass: ");
//    }
//    public Glass getGlass() {
//        return this.glass;
//    }
    public Game getG() {
        return g;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setMappa(String[][] tiles) throws IOException {
        this.circondario = tiles.length;
        mappa = new TileMap(circondario, circondario);
        Image img = null;
        for (int i = 0; i < tiles.length; i++) {
            for (int j = 0; j < tiles.length; j++) {
                img = alloverdev.Assets.bufferedImage("Tiles/" + tiles[i][j] + ".jpg");
                this.mappa.setTile(j, i, img);
//                System.out.print(tiles[j][i]+" ");
            }
//            System.out.println("\n");
        }
        if (!creato_frame) {
            initComponents();
        }
        this.repaint();
        creato_frame = true;

    }

    public void setMappa_calpestabile(String[][] mappa_calpestabile) {
        this.circondario = mappa_calpestabile.length;
        this.mappa_calpestabile = new String[circondario][circondario];

        for (int i = 0; i < mappa_calpestabile.length; i++) {
            for (int j = 0; j < mappa_calpestabile.length; j++) {

                this.mappa_calpestabile[i][j] = mappa_calpestabile[j][i];
                System.out.print(this.mappa_calpestabile[i][j] + " ");
            }
            System.out.println("\n");
        }
    }

    public String[][] getMappa_calpestabile() {
        return mappa_calpestabile;
    }

    public JTextArea getTxt() {
        return txt;
    }

    public int getCircondario() {
        return circondario;
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new AgentMap();
            }
        });
    }

}
