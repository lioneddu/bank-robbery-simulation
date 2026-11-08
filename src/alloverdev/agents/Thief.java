package alloverdev.agents;

import alloverdev.astar.AStar;
import alloverdev.interfaces.AgentsIF;
import alloverdev.maps.AgentMap;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextArea;
import alloverdev.broker.*;

public class Thief extends Agent implements AgentsIF {

    private AgentType type;
    private AgentMap agentmap;
    private String[][] tiles;
    private String[][] mappa_calpestabile;
    private int winningRob; //rapine vincenti
    private double robberyTot; //Totale denaro rubato
    private double moneyStolen; //denaro rubato rapina corrente
    private boolean otRunning;
    private boolean inBank;
    private boolean inBush;
    private boolean gold;
    private boolean inCaveau;
    private boolean inWaitingSpace;
    private boolean myTurn;
    private boolean ready;
    private OperationsThread ot;
    private boolean bankOpen;
    private boolean inWalk;
    private String action;

    public Thief(Connection conn) {
        super(conn);
        this.type = AgentType.THF;
        this.agentmap = new AgentMap(this);//passo anche l'agente (this) così alla chiusura della finestra posso richiamare il metodo subscribe
        this.agentmap.setType(type.name());
        this.winningRob = 0;
        this.robberyTot = 0;
        this.moneyStolen = 0.0;
        this.otRunning = false;
        this.inBank = false;
        this.inCaveau = false;
        this.gold = false;
        this.ready = false;
        this.inWaitingSpace = false;
        this.myTurn = false;
        this.bankOpen = false;
        this.inWalk = false;
        this.inBush = false;
        action = "";
    }

    /**
     * Metodo per registrare le activity di un agente
     *
     * @param b booleano per deregistrare (true) o registrare (false)
     *
     */
    @Override
    public void subscribe(boolean b) {
        SubscribeMessage submsg = new SubscribeMessage();
        if (!b) {
            submsg.addActivity(THF_1);
            submsg.addActivity(THF_2);
            submsg.addActivity(THF_3);
            submsg.addActivity(THF_5);
            submsg.addActivity(KILLME);
        }
        conn.send(submsg);
        showStatus(submsg, true);
    }

    /*
    Thread utile a segnalare la continua presenza di un agente al supervisor
    *Viene avviato parallelamente al thread principale dell'agente
     */
    private class PresenceThread extends Thread {

        private static final int WAIT = 5000;

        public void run() {
            for (;;) {
                NotifyMessage nmsg = new NotifyMessage();
                nmsg.addNext(SUP_2, getType().toString(), "ANY");
                conn.send(nmsg);
                checkStatus(nmsg, true);
                try {
                    Thread.sleep(WAIT);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    //Thread per le operazioni
    private class OperationsThread extends Thread {

        public void run() {

            otRunning = OperationsThread.currentThread().isAlive(); // Setta il flag a true in modo che il thread non venga più avviato

            for (;;) {

                System.out.println("Dentro OT");

                while (!isBankOpen()) {
                    walking();
                }
                moveToBankEntry(); // chiede di andare in banca se non ci si trova già
                moveToCaveau();
                moveToGold();

                //moveToBankExit();
                escape();

                try {
                    Thread.currentThread().sleep(5000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Thief.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }
    }

    private void reqNeigh() {
        NotifyMessage nmsg = new NotifyMessage();
        nmsg.addNext(SUP_1, getType().toString(), "ANY");
        conn.send(nmsg);
        checkStatus(nmsg, true);
    }

    /**
     * Run del thread del ladro (start dell'agent dal main)
     */
    @Override
    public void run() {

        new PresenceThread().start(); //thread parallelo che segnala la presenza continuamente
        Thread.currentThread().setName("CT_" + getType() + "_" + getConn().getLogin());
        reqNeigh();

        ot = new OperationsThread(); //richieste vicinato
        ot.setName("OT_" + getType() + "_" + getConn().getLogin());

        // Ciclo perenne. Recupera le activities da svolgere e le svolge
        for (;;) {
            EngageMessage engmsg = new EngageMessage();

            if (!otRunning && ready) {
                ot.start();
            }
            //====================================
            conn.send(engmsg, true);
            checkStatus(engmsg, true);  // Se status fosse KO saremmo gia' usciti qui !!

            // Se lo stato e' OK fornisce la Notify di completed (o refused)
            XMLInterface task = engmsg.getTask();

            NotifyMessage nmsg;
            // If sender == recipient, refuse the message
            if (conn.getLogin().equals(task.getProperty("Scheduler"))) {
                nmsg = new NotifyMessage(task.getProperty("Activity"),
                        task.getProperty("Serial"),
                        "REFUSED",
                        null);
                conn.send(nmsg);
                checkStatus(nmsg, true);
            } else {
                String activity = task.getProperty("Activity");
                System.out.println("L'Activity ricevuta e': " + activity + " inviata da: " + task.getProperty("Scheduler"));

                if (THF_1.equals(activity)) {
                    System.out.println("Voglio muovermi \n");
                    movement(task);
                    updateAgentMap();
                } else if (THF_2.equals(activity)) {
                    try {
                        doNeigh(task);
                        updateAgentMap();
                    } catch (IOException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (THF_3.equals(activity)) {

                    System.out.println("Ho la refurtiva ... via di qua subito!");
                    updateStealedMoney(task);

                } else if (THF_5.equals(activity)) {
                    System.out.println("Esito aggiornamento posizione");
                    updatePos(task);
                    updateAgentMap();

                } else if (KILLME.equals(activity)) {
                    NotifyMessage nmsg3 = new NotifyMessage();
                    nmsg3.setActivity(task.getProperty("Activity"));
                    nmsg3.setSerial(task.getProperty("Serial"));
                    nmsg3.setOutcome("COMPLETED");
                    conn.send(nmsg3);
                    checkStatus(nmsg3, true);
                    shutdown();
                }

            }
        }
    }

    //Se ricevo messaggio di aggiornamento effettuato sveglio il thread neighborhood per effettuare richiesta vicinato
    private synchronized void updatePos(XMLInterface task) {

        String[] payload = task.getProperty("Payload").split("_");
        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);

        System.out.println("OK - Sveglio neighborhood");
        conn.send(nmsg2);
        if (payload[0].equals("UPDATEDPOS")) {
            //Se mi è stata aggiornata la posizione
            if (payload.length == 3) {
                // Controllo se ho raggunto la posizione finale (tramite keywords inviate dal supervisor)
                if (payload[1].equals("LAST") && payload[2].equals("BANKENTRY")) {
                    System.out.println("InBank");
                    this.inBank = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("CAVEAUENTRY")) {
                    System.out.println("InCaveau");
                    this.inCaveau = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("REACHGOLD")) {
                    System.out.println("toGold");
                    this.gold = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("BANKEXIT")) {
                    System.out.println("NotInBank");
                    this.inBank = false;
                } else if (payload[1].equals("LAST") && payload[2].equals("WALKING")) {
                    System.out.println("Sto aspettando");
                    this.inWalk = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("BUSH")) {
                    System.out.println("In bush");
                    this.inBush = true;
                    this.inBank = false;
                    action = "Hiding\nfrom guard!";
                    this.moneyStolen = 0;
                    this.winningRob++;
                }
            }

            reqNeigh();
        }
    }

    private void updateStealedMoney(XMLInterface task) {

        String[] payload = task.getProperty("Payload").split("_");
        if (payload[0].equals("ROBTHEBANK")) {
            this.moneyStolen = Double.parseDouble(payload[1]);

            this.robberyTot += this.moneyStolen;
            updateAgentMap();

            NotifyMessage nmsg2;
            nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                    task.getProperty("Serial"),
                    "COMPLETED",
                    null);
            conn.send(nmsg2);
            showStatus(nmsg2, true);
        } else if (payload[0].equals("PLANFAILED")) {
            System.out.println("Plan failed");
            this.type = AgentType.GRV;
            NotifyMessage nmsg2;
            nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                    task.getProperty("Serial"),
                    "COMPLETED",
                    null);
            System.out.println("Type: " + getType().toString());
            System.out.println("Data Plan failed: " + new Date());
            nmsg2.addNext(SUP_2, getType().toString(), "ANY");
            conn.send(nmsg2);
            showStatus(nmsg2, true);
        }
    }

    private void movement(XMLInterface task) {
        System.out.println("Movement");
        NotifyMessage nmsg = new NotifyMessage();

        String nick = task.getProperty("Scheduler"); //idAgent per get nel glass
        System.out.println(nick);

        String[] payload = task.getProperty("Payload").split("_");

        if (payload[0].equals("APPROVEDMOV")) { //movimento approvato
            System.out.println("APPROVED");
            String[] pos = payload[1].split(",");

            int y1 = (Integer.parseInt(pos[1]));
            int x1 = (Integer.parseInt(pos[0]));

            setBankOpen(payload[2].equals("true"));

            System.out.println("Dest x1 " + x1 + ", Dest y1 " + y1);

            int[][] cleanedNotWalkableTiles2 = getCleanedNotWalkableTiles();

            AStar aStar1 = new AStar(mappa_calpestabile.length, mappa_calpestabile.length, mappa_calpestabile.length / 2, mappa_calpestabile.length / 2, y1, x1, cleanedNotWalkableTiles2);
            aStar1.display();
            aStar1.process();

            aStar1.displaySolution();

            List pathList = aStar1.getPathList();
            System.out.println("PathList:" + pathList);

            //se il pathlist è vuoto, mando una richiesta al SUP per rimanere esattamente dove mi trovo
            if (pathList.size() == 0) {

                nmsg.addNext(SUP_3, "UPDATEPOS_" + Integer.toString(this.agentmap.getCircondario() / 2) + "," + Integer.toString(this.agentmap.getCircondario() / 2) + "_" + getType() + "_NOTLAST", "ANY");
                System.out.println("Agent - Resto nella mia posizione");
                nmsg.setActivity(task.getProperty("Activity"));
                nmsg.setSerial(task.getProperty("Serial"));
                nmsg.setOutcome("COMPLETED");
                conn.send(nmsg);
                checkStatus(nmsg, true);
                System.out.println("Richiesta UPDATEPOS");

            } else {

                String last_mov = "";
                if (pathList.size() == 1) {
                    System.out.println("LAST mov PATH");
                    last_mov = "_LAST";
                } else {
                    last_mov = "_NOTLAST";
                }

                nmsg.addNext(SUP_3, "UPDATEPOS_" + pathList.get(pathList.size() - 1).toString() + "_" + getType() + last_mov, "ANY");
                System.out.println("Agent - Voglio muovermi in: " + pathList.get(pathList.size() - 1).toString());
                nmsg.setActivity(task.getProperty("Activity"));
                nmsg.setSerial(task.getProperty("Serial"));
                nmsg.setOutcome("COMPLETED");
                conn.send(nmsg);
                checkStatus(nmsg, true);

                System.out.println("Richiesta UPDATEPOS");

            }
        }

    }

    //metodo che, ricevuto il circondario dal supervisor, setta le tile e la mappa calpestabile in questa classe e nella classe che crea la gui 
    private synchronized void doNeigh(XMLInterface task) throws IOException {

        String[] ch = task.getProperty("Payload").split(",");
        tiles = new String[Integer.parseInt(ch[0])][Integer.parseInt(ch[0])];
        mappa_calpestabile = new String[Integer.parseInt(ch[0])][Integer.parseInt(ch[0])];
        int indice = 1;
        for (int i = 0; i < Integer.parseInt(ch[0]); i++) {
            for (int j = 0; j < Integer.parseInt(ch[0]); j++) {
                tiles[i][j] = ch[indice];
                indice++;
            }
        }
        for (int i = 0; i < Integer.parseInt(ch[0]); i++) {
            for (int j = 0; j < Integer.parseInt(ch[0]); j++) {
                mappa_calpestabile[i][j] = ch[indice];
                indice++;
            }
        }

        this.agentmap.setMappa_calpestabile(mappa_calpestabile);
        this.agentmap.setMappa(tiles);
        this.agentmap.setType(this.type.name());

        ready = true;

        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);
        System.out.println("Chiusura Richiesta vicinato");
        conn.send(nmsg2);

        notify();

    }

    private synchronized void moveToBankEntry() {

        while (!inBank) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_BANKENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Let's try to\nsteal some gold!";
            try {
                System.out.println("THF - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }

    }

    /*
    * Metodo per la fuga. Il ladro cerca di scappare dirigendosi verso un cespuglio
     */
    private synchronized void escape() {
        gold = false;
        inCaveau = false;
        while (!inBush) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_BUSH_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "I'm running away!";
            try {
                System.out.println("THF - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        inBush = false;

    }

    /*
    * Metodo per tentatre il furto. Il ladro cerca raggiungere l'oro
     */
    private synchronized void moveToGold() {

        while (!gold) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_REACHGOLD_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Wow, what a\ntreasure!";
            try {
                System.out.println("THF - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
    }

//Metodo del ladro per dirigersi verso il caveau
    private synchronized void moveToCaveau() {
        while (!inCaveau) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_CAVEAUENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "I can't wait\nto see the gold!";
            try {
                System.out.println("THF - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
    }

    private synchronized void walking() {

        while (!inWalk) {
            if (isBankOpen()) {
                NotifyMessage nmsg = new NotifyMessage();
                nmsg.addNext(SUP_3, "STOP_" + getType(), "ANY");
                conn.send(nmsg);
                checkStatus(nmsg, true);
                try {
                    System.out.println("CLN - Metto OT in wait ");
                    wait();
                } catch (InterruptedException ex) {
                }
                inWalk = false;
                break;
            }
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_WALKING_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Let's walk and\nthink about a plan!";
            try {
                System.out.println("THF - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        inWalk = false;
    }

    // Metodo che restituisce le posizioni delle tile non calpestabili al netto di quella in cui è presente l'agente corrente
    private int[][] getCleanedNotWalkableTiles() {

        int[] vect = new int[2];

        //Ciclo per determinare il numero di tile non calpestabili ---> ottengo size
        int k = 0;
        for (int i = 0; i < mappa_calpestabile.length; i++) {
            for (int j = 0; j < mappa_calpestabile[i].length; j++) {
                if (mappa_calpestabile[i][j].equals("1") || mappa_calpestabile[i][j].equals("2")) {
                    continue;
                } else {

                    k++;
                }
            }
        }

        //Uso la size per creare un vettore più piccolo di uno rispetto ad essa
        //Inserisco tutte le posizioni meno quella che corrisponde all'agente
        int[][] vect2 = new int[k - 1][2];
        int z = 0;
        for (int i = 0; i < mappa_calpestabile.length; i++) {
            for (int j = 0; j < mappa_calpestabile[i].length; j++) {
                if (mappa_calpestabile[i][j].equals("1") || mappa_calpestabile[i][j].equals("2")) {
                    continue;
                } else if (i == mappa_calpestabile.length / 2 && j == mappa_calpestabile.length / 2) {
                    continue;
                } else {

                    vect2[z][0] = j;
                    vect2[z][1] = i;
                    System.out.println("Vect2: " + vect2[z][0] + "," + vect2[z][1]);
                    z++;
                }
            }
        }

//        System.out.println("CleanedClient" + cleanedNotWalkableTiles.get(0)[0] + "," + cleanedNotWalkableTiles.get(0)[1]);
        return vect2;
    }

    private void updateAgentMap() {
        String s = agentmap.statsAgent(this);
        JTextArea txt = agentmap.getTxt();
        txt.setText(s);
    }

    private void shutdown() {
        subscribe(true);
        conn.terminate(); // stops only this agent (all agents share the same JVM)
    }

    /**
     * Metodo che restituisce il tipo di agente (agentType)
     *
     * @return type
     */
    @Override
    public AgentType getType() {
        return type;
    }

    public int getWinningRob() {
        return winningRob;
    }

    public double getRobberyTot() {
        return robberyTot;
    }

    public double getMoneyStolen() {
        return moneyStolen;
    }

    @Override
    public String toString() {
        return "Thief " + "Action: \n" + action + 
                "\nWinning Rob: \n" + winningRob + 
                "\nRobbery Tot: \n" + String.format("%.2f", robberyTot) + 
                "\nMoney Stolen: \n" + String.format("%.2f", moneyStolen);
    }

    public boolean isBankOpen() {
        return this.bankOpen;
    }

    public void setBankOpen(boolean bankOpen) {
        this.bankOpen = bankOpen;
    }
}
