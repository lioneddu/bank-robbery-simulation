package alloverdev.maps;

import alloverdev.agents.Supervisor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JFrame;

public class MapMain {

    private Glass glass;
    private Game g;
    private Supervisor sup;
    private int circondario;

    public MapMain(Supervisor ag, int circondario) {
        initComponents(circondario);
        sup = ag;
        this.circondario = circondario;
    }

    public MapMain() {
        initComponents(circondario);
    }

    private void initComponents(int circondario) {

        g = new Game(alloverdev.Assets.path("Matrice_mappa.txt"), alloverdev.Assets.path("Matrice_mappa_calpestabile.txt"), circondario);

        JFrame frame = new JFrame("Bank Robbery");

        ImageIcon icon = new ImageIcon(alloverdev.Assets.image("Immagini_menu/bank.png"));
        frame.setIconImage(icon.getImage()); // Aggiunta logo banca nella title bar
        
        // ASSEGNARE IL SIZE AL FRAME e' BRUTTO, deve essere calcolato in automatico
        //SetSIze = (NumColonneMappa*NumTIleLarg) , (NumRigheMappa*NumTileAltezza - TitleBarHeight)
        // frame.setSize(g.getMappa().getWidth() * g.getTileWidth() + frame.getInsets().right + frame.getInsets().left, g.getMappa().getHeight() * g.getTileHeight() + frame.getInsets().top + frame.getInsets().bottom);

        //Listener su chiusura finestra che esegue end session e deregistra l'agente
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent evt) {
                endSession(evt);
            }

        });
        frame.setResizable(false);

        // Creiamo un Border Layout per il JFrame
        frame.setLayout(new BorderLayout());
        // Dimensioniamo opportunamente la mappa, e assegnamo la mappa a CENTER
        g.setPreferredSize(new Dimension(g.getMappa().getWidth() * g.getTileWidth(), 
                                         g.getMappa().getHeight() * g.getTileHeight()));
        frame.add(g, BorderLayout.CENTER);        
        
        initGlassPane(frame, g);
        
        frame.pack();

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(alloverdev.WindowLayout.mainWindow(frame.getSize()));
        frame.setVisible(true);

    }

    /*
    Metodo avviato alla chiusura della mappa con il circondario del supervisor
    Effettua la deregistrazione del supervisor e chiude il thread
     */
    private void endSession(WindowEvent evt) {
        System.out.println("End Session");
        sup.endGame();
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(MapMain.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        System.exit(0);
    }

    public void initGlassPane(JFrame frame, Game g) {
        Glass glass = new Glass();
        frame.setGlassPane(glass);
        glass.setVisible(true);
        glass.setCaselle_spawn(g.getCaselle_spawn());
        glass.setMappa_calpestabile(g.getMappa_calpestabile(), g.getMappa_calpestabile().length, g.getMappa_calpestabile()[0].length);
        this.glass = glass;

    }

    public Glass getGlass() {
        return glass;
    }

    public Game getG() {
        return g;
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MapMain();
            }
        });
    }

}
