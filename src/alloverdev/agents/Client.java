package alloverdev.agents;

import alloverdev.operation.Account;
import alloverdev.astar.AStar;
import alloverdev.interfaces.AgentsIF;
import alloverdev.maps.AgentMap;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextArea;
import alloverdev.broker.*;

public class Client extends Agent implements AgentsIF {

    private AgentType type;
    private AgentMap agentmap;
    private String[][] tiles; //tiles del circondario da disegnare
    private String[][] mappa_calpestabile; //matrice del circondario 
    private double wallet; //denaro fisicamente in possesso
    private boolean otRunning;
    private Account acc; //conto del cliente
    private boolean ready; // Se true indica che l'agente è pronto - primo vicinato ricevuto
    private OperationsThread ot; //thread per fare operazioni
    private double saldo; //saldo del conto
    private boolean richiesta; //variabile per controllare quando è presente una richiesta di operazione bancaria
    private boolean inBank;
    private boolean myTurn;
    private boolean inDesk;
    private String employer;
    private boolean richiestaCompletata;
    private boolean richiestaSlotCompletata;
    private boolean richiestaSlot;
    private boolean inSalaSlot;
    private boolean inSlot;
    private boolean justInBank;
    private boolean justInSlot;
    private String action;
    private boolean inWalk;
    private boolean inPoorWalk;
    private boolean haveTkt;
    private boolean bankOpen;
    private double soldiPersi; //soldi persi alle slot
    private double soldiVinti;  //soldi vinti alle slot
    private boolean nextGiocata; // controlla la richiesta di giocate successive sulla stessa slot

    public Client(Connection conn, double wallet) {
        super(conn);
        this.type = AgentType.CLN;
        this.agentmap = new AgentMap(this); //passo anche l'agente (this) così alla chiusura della finestra posso richiamare il metodo subscribe
        this.agentmap.setType(type.name());
        this.wallet = wallet;
        this.justInBank = false;
        this.richiestaSlotCompletata = false;
        this.justInSlot = false;
        this.otRunning = false;
        this.ready = false;
        this.saldo = 1.0;
        this.richiesta = false;
        this.inBank = false;
        this.myTurn = false;
        this.inDesk = false;
        this.employer = null;
        this.richiestaCompletata = false;
        this.richiestaSlot = false;
        this.inSalaSlot = false;
        this.inSlot = false;
        this.soldiPersi = 0.0;
        this.soldiVinti = 0.0;
        this.inWalk = false;
        this.inPoorWalk = false;
        this.bankOpen = false;
        this.haveTkt = false;
        this.nextGiocata = false;
        this.action = "";
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
            submsg.addActivity(CLN_1);
            submsg.addActivity(CLN_2);
            submsg.addActivity(CLN_3);
            submsg.addActivity(CLN_4);
            submsg.addActivity(CLN_5);
            submsg.addActivity(CLN_7);
            submsg.addActivity(ALARM);
            submsg.addActivity(KILLME);
        }
        conn.send(submsg);
        showStatus(submsg, true);
    }

    /*
    Thread utile a segnalare la continua presenza di un agente al supervisor
    *Viene avviato parallelamente al thread principale dell'agente una volta che è ready
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

                double random = Math.round(Math.random() * 100.0) / 100.0;
                System.out.println("\nrandom vale: " + random);
                //se al suo ultimo spostamento il cliente non è stato in precedenza in banca o alla slot, allora:
                if (!justInBank && !justInSlot) {
                    if (wallet + saldo == 0) {
                        System.out.println("Faccio una passeggiata");
                        poorwalking();
                    } else if ((wallet == 0 && saldo > 0) && isBankOpen()) {
                        if (random <= 0.5) {
                            System.out.println("Il mio portafoglio è vuoto voglio effettuare una operazione bancaria prelievo");
                            doOperazioneBancaria("prelievo");
                        } else {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        }
                    } else if ((wallet == 0 && saldo > 0) && !isBankOpen()) {
                        System.out.println("Faccio una passeggiata");
                        walking();
                    } else if ((wallet > 0 && saldo == 0) && isBankOpen()) {
                        if (random <= 0.3) {
                            System.out.println("Il mio portafoglio è in positivo posso effettuare una operazione bancaria di deposito");
                            doOperazioneBancaria("deposito");
                        } else if (random >= 0.7) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random < 0.7 && random > 0.3) {
                            System.out.println("Vado alle slot");
                            requestSlot();
                        }
                    } else if ((wallet > 0 && saldo == 0) && !isBankOpen()) {
                        if (random >= 0.5) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random < 0.5) {
                            System.out.println("Vado alle slot");
                            requestSlot();
                        }
                    } else if ((wallet > 0 && saldo > 0) && isBankOpen()) {
                        if (random <= 0.2) {
                            System.out.println("Il mio portafoglio è in positivo posso effettuare una operazione bancaria di deposito");
                            doOperazioneBancaria("deposito");
                        } else if (random >= 0.8) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random < 0.8 && random >= 0.5) {
                            System.out.println("Vado alle slot");
                            requestSlot();
                        } else if (random < 0.5 && random > 0.2) {
                            System.out.println("Il mio portafoglio è in positivo voglio effettuare una operazione bancaria prelievo");
                            doOperazioneBancaria("prelievo");
                        }
                    } else if ((wallet > 0 && saldo > 0) && !isBankOpen()) {
                        if (random >= 0.5) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random < 0.5) {
                            System.out.println("Vado alle slot");
                            requestSlot();
                        }
                    }
                }
                double random1 = Math.round(Math.random() * 100.0) / 100.0;
                System.out.println("\nrandom1 vale: " + random1);
                //se al suo ultimo spostamento il cliente è stato in precedenza in banca, allora:
                if (justInBank && !justInSlot) {
                    if (wallet + saldo == 0) {
                        System.out.println("Faccio una passeggiata");
                        poorwalking();
                    } else if (wallet == 0 && saldo > 0) {
                        System.out.println("Faccio una passeggiata");
                        walking();
                    } else if (wallet > 0 && saldo == 0) {
                        if (random1 >= 0.5) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random1 < 0.5) {
                            System.out.println("Vado alle slot");
                            requestSlot();
                        }
                    } else if (wallet > 0 && saldo > 0) {
                        if (random1 >= 0.5) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random1 < 0.5) {
                            System.out.println("Vado alle slot");
                            requestSlot();
                        }
                    }
                }
                double random2 = Math.round(Math.random() * 100.0) / 100.0;
                System.out.println("\nrandom2 vale: " + random2);
                //se al suo ultimo spostamento il cliente è stato in precedenza alla sala slot, allora:
                if (!justInBank && justInSlot) {
                    if (wallet + saldo == 0) {
                        System.out.println("Faccio una passeggiata");
                        poorwalking();
                    } else if ((wallet == 0 && saldo > 0) && isBankOpen()) {
                        if (random2 <= 0.5) {
                            System.out.println("Il mio portafoglio è vuoto voglio effettuare una operazione bancaria prelievo");
                            doOperazioneBancaria("prelievo");
                        } else {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        }
                    } else if ((wallet == 0 && saldo > 0) && !isBankOpen()) {
                        System.out.println("Faccio una passeggiata");
                        walking();
                    } else if ((wallet > 0 && saldo == 0) && isBankOpen()) {
                        if (random2 <= 0.3) {
                            System.out.println("Il mio portafoglio è in positivo posso effettuare una operazione bancaria di deposito");
                            doOperazioneBancaria("deposito");
                        } else if (random2 > 0.3) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        }
                    } else if ((wallet > 0 && saldo == 0) && !isBankOpen()) {
                        System.out.println("Faccio una passeggiata");
                        walking();

                    } else if ((wallet > 0 && saldo > 0) && isBankOpen()) {
                        if (random2 <= 0.3) {
                            System.out.println("Il mio portafoglio è in positivo posso effettuare una operazione bancaria di deposito");
                            doOperazioneBancaria("deposito");
                        } else if (random2 >= 0.7) {
                            System.out.println("Faccio una passeggiata");
                            walking();
                        } else if (random2 < 0.7 && random2 > 0.3) {
                            System.out.println("Il mio portafoglio è in positivo voglio effettuare una operazione bancaria prelievo");
                            doOperazioneBancaria("prelievo");
                        }
                    } else if ((wallet > 0 && saldo > 0) && !isBankOpen()) {
                        System.out.println("Faccio una passeggiata");
                        walking();

                    }

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
     * Run del thread del client (start dell'agent dal main)
     */
    @Override
    public void run() {

        new PresenceThread().start(); //thread parallelo che segnala la presenza continuamente
        Thread.currentThread().setName("CT_" + getType() + "_" + getConn().getLogin());

        reqNeigh();
        ot = new OperationsThread(); //istanzio ma non AVVIO subito

        ot.setName("OT_" + getType() + "_" + getConn().getLogin());

        //Se l'agente è operativo con la sua gui può procedere a richiedere al supervisor qualche azione
        // Ciclo perenne. Recupera le activities da svolgere e le svolge
        for (;;) {
            EngageMessage engmsg = new EngageMessage();

            //Se agente ha ricevuto il suo vicinato e l'operations thread non è stato ancora avviato, lo avvio
            if (!otRunning && ready) {
                ot.start();
            }

            conn.send(engmsg, true);
            checkStatus(engmsg, true);

            // Se lo stato e' OK fornisce la Notify di completed (o refused)
            XMLInterface task = engmsg.getTask();

            NotifyMessage nmsg;
            // Se sender == recipient, rigetto il messaggio
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

                if (CLN_1.equals(activity)) {

                    System.out.println("Voglio muovermi \n");
                    movement(task);

                } else if (CLN_2.equals(activity)) {

                    try {

                        //In risposta al circondario inviato dal supervisor si esegue questo metodo
                        doNeigh(task);
                        updateAgentMap();
                    } catch (IOException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else if (CLN_3.equals(activity)) {
                    updateWallet(task);
                    richiesta = false;

                    updateAgentMap();

                } else if (CLN_4.equals(activity)) {
                    updateSaldo(task);
                    updateAgentMap();

                    NotifyMessage nmsg2;
                    nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                            task.getProperty("Serial"),
                            "COMPLETED",
                            null);

                    //System.out.println("OK - Sveglio neighborhood");
                    conn.send(nmsg2);

                } else if (CLN_5.equals(activity)) {

                    System.out.println("Esito aggiornamento posizione");
                    updatePos(task);

                } else if (CLN_7.equals(activity)) {
                    updateSlot(task);
                    updateAgentMap();

                } else if (ALARM.equals(activity)) {

                    NotifyMessage nmsg2;
                    nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                            task.getProperty("Serial"),
                            "COMPLETED",
                            null);

                    //System.out.println("OK - Sveglio neighborhood");
                    conn.send(nmsg2);

                } else if (KILLME.equals(activity)) {

                    NotifyMessage nmsg3 = new NotifyMessage();
                    nmsg3.setActivity(task.getProperty("Activity"));
                    nmsg3.setSerial(task.getProperty("Serial"));
                    nmsg3.setOutcome("COMPLETED");
                    conn.send(nmsg3);
                    checkStatus(nmsg3, true);
                    shutdown();

                }

                //conn.send(nmsg);
                //checkStatus(nmsg, true);
            }
        }
    }

    //Se ricevo messaggio di aggiornamento effettuato sveglio il thread neighborhood per effettuare richiesta vicinato
    private void updatePos(XMLInterface task) {

        String[] payload = task.getProperty("Payload").split("_");
        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);

        //System.out.println("OK - Sveglio neighborhood");
        conn.send(nmsg2);

        if (payload[0].equals("UPDATEDPOS")) {
            //Se mi è stata aggiornata la posizione
            if (payload.length == 3) {
                // Controllo se ho raggunto la posizione finale (tramite keywords inviate dal supervisor)
                if (payload[1].equals("LAST") && payload[2].equals("BANKENTRY")) {
                    System.out.println("InBank");
                    inBank = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("DESK")) {
                    System.out.println("InDesk");
                    inDesk = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("BANKEXIT")) {
                    System.out.println("NotInBank");
                    inDesk = false;
                    inBank = false;
                    myTurn = false;
                    employer = null;
                    this.haveTkt = false;
                    justInBank = true;
                    justInSlot = false;
                    richiestaCompletata = false;
                } else if (payload[1].equals("LAST") && payload[2].equals("SLOTENTRY")) {
                    System.out.println("InSlotEntry");
                    inSalaSlot = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("SLOT")) {
                    System.out.println("InSlot");
                    inSlot = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("SLOTEXIT")) {
                    System.out.println("InSlotExit");
                    inSlot = false;
                    inSalaSlot = false;
                    justInBank = false;
                    justInSlot = true;
                    richiestaSlotCompletata = false;
                } else if (payload[1].equals("LAST") && payload[2].equals("WALKING")) {
                    System.out.println("Ho terminato la mia passeggiata");
                    this.inWalk = true;
                    justInBank = false;
                    justInSlot = false;
                } else if (payload[1].equals("LAST") && payload[2].equals("POORWALKING")) {
                    System.out.println("Ho terminato la mia lunga passeggiata");
                    this.inPoorWalk = true;
                    justInBank = false;
                    justInSlot = false;
                } else if (payload[1].equals("LAST") && payload[2].equals("HAVETKT")) {
                    System.out.println("Ho preso il biglietto");
                    this.haveTkt = true;
                }
            }

            reqNeigh();

        }
    }

    private void movement(XMLInterface task) {
        System.out.println("Movement");
        NotifyMessage nmsg = new NotifyMessage();

        String nick = task.getProperty("Scheduler"); //idAgent per get nel glass

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

//Disiscrive agente e chiude gui
    private void shutdown() {
        subscribe(true);
        conn.terminate(); // stops only this agent (all agents share the same JVM)
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

        return vect2;
    }

    //Metodo che restituisce vicinato dell'agente
    private String[][] getNeighMov() {

        String str[][] = new String[mappa_calpestabile.length][mappa_calpestabile.length];

        for (int i = 0; i < mappa_calpestabile.length; i++) {
            for (int j = 0; j < mappa_calpestabile.length; j++) {
                str[i][j] = i + "," + j;
            }
        }
        return str;
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

        ready = true; // dopo il primo vicinato ricevuto l'agente è ready

        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);
        System.out.println("Chiusura Richiesta vicinato");
        conn.send(nmsg2);

        notify(); //Sveglia l'operations thread

    }

    private synchronized void moveToBank() {
        //Se agente non in banca si muove verso la porta
        while (!inBank) {//controllo che la banca sia già aperta
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_BANKENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "It's my turn!";
            try {
                System.out.println("CLN - Mi sto muovendo in banca ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private synchronized void moveToTicket() {
        //Se agente non in banca si muove verso la porta
        while (!haveTkt) {//controllo se ho il ticket
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_HAVETKT_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Taking \nmy ticket!";

            try {
                System.out.println("CLN - Mi sto prendendo il biglietto ");
                wait();
            } catch (InterruptedException ex) {
            }
        }
        NotifyMessage nmsg = new NotifyMessage();
        nmsg.addNext(SUP_7, "INSERTME_" + getType(), "ANY");
        conn.send(nmsg);
        checkStatus(nmsg, true);
        action = "Inserting \ninto queue!";
    }

    //Richiede continuamente di uscire dalla banca finchè non la raggiunge
    private synchronized void moveToBankExit() {

        while (inBank) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_BANKEXIT_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Leaving \nbank!";

            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {

            }
        }

    }

    private synchronized void walking() {

        while (!inWalk) {
            if ((isBankOpen() && employer == null && !haveTkt && (saldo + wallet > 0) && wallet == 0 && !justInSlot) || myTurn) {
                NotifyMessage nmsg = new NotifyMessage();
                nmsg.addNext(SUP_3, "STOP_" + getType(), "ANY");
                conn.send(nmsg);
                checkStatus(nmsg, true);
                try {
                    System.out.println("CLN - Metto OT in wait ");
                    wait();
                } catch (InterruptedException ex) {
                }
                break;
            }

            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_WALKING_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Going for\na walk!";
            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        inWalk = false;
    }

    private synchronized void poorwalking() {

        while (!inPoorWalk) {

            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_POORWALKING_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Going for\na long walk!";
            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        inPoorWalk = false;
    }

    //Chiede OB verificando di essere prima in banca, aver fatto la coda (suo turno) ed essere al desk
    private synchronized void doOperazioneBancaria(String operationType) {

        while (!isBankOpen()) {
            walking();
        }

        moveToTicket();

        while (employer == null) {
            walking();
        }

        moveToDesk(employer); // chiede di raggiungere il desk dell'impiegato che lo ha accettato
        System.out.println("In Desk 1" + inDesk);
        while (richiesta || !inDesk) { //Se la richiesta è già stata fatta oppure non è al desk aspetta            
            try {
                wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        System.out.println("In Desk 2" + inDesk);
        //Se si trova in posizione corretta allora posso fare una richiesta
        if (inBank && inDesk) {
            double deposito;
            richiesta = true;
            double cifra = Math.floor(((Math.random() * 100) * 100.0) / 100.0);

            NotifyMessage nmsg = new NotifyMessage();

            //se il wallet ha meno di 20 invia una richiesta di prelievo
            //altrimenti sceglie randomicamente cosa fare in funzione di i
            if (operationType.equals("prelievo")) {
                System.out.println("devo fare un prelievo di " + (-cifra));
                nmsg.addNext(EMP_3, "OP," + (-cifra) + "," + wallet + "," + conn.getLogin(), employer);
            } else if (operationType.equals("deposito")) {
                while (cifra > 50) {
                    cifra = Math.floor(((Math.random() * 100) * 100.0) / 100.0);
                }
                if (wallet > cifra) {
                    deposito = Math.floor((wallet - cifra) * 100.0) / 100.0;
                    System.out.println("devo fare un deposito di " + deposito);
                    nmsg.addNext(EMP_3, "OP," + deposito + "," + wallet + "," + conn.getLogin(), employer);
                    wallet -= deposito;
                    updateAgentMap();
                } else {
                    deposito = Math.floor((wallet - 10) * 100.0) / 100.0;
                    System.out.println("devo fare un deposito di " + deposito);
                    nmsg.addNext(EMP_3, "OP," + deposito + "," + wallet + "," + conn.getLogin(), employer);
                    wallet -= deposito;
                    updateAgentMap();
                }
            }

            conn.send(nmsg);
            checkStatus(nmsg, true);

            while (!richiestaCompletata) {
                reqNeigh();
                action = "Waiting for\nthe operation!";
                System.out.println("ho chiesto il vicinato da fermo al desk!!!!");
                try {
                    System.out.println("Aspetto finchè richiesta completata");
                    wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
                try {
                    Thread.currentThread().sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            //A richiesta completata esce dalla banca
            moveToBankExit();
        }
    }
//Se non al desk cerca di raggiungerlo

    private synchronized void moveToDesk(String employer) {
        moveToBank(); // chiede di andare in banca se non ci si trova già       
        //Finchè non raggiungo il desk chiedo spostamento verso di esso
        while (!inDesk) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_DESK" + employer + "_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Going to \ndesk!";
            System.out.println("Sono in MoveToDesk");
            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
    }

    private void updateWallet(XMLInterface task) {
        //Doppia funzione

        //Se il payload comincia con YOURTURN l'impiegato mi sta chiamando al desk
        if (task.getProperty("Payload").startsWith("YOURTURN")) {

            myTurn = true;
            employer = task.getProperty("Payload").split("_")[1]; //impiegato che ha chiamato l'agente
            System.out.println("Sono in update Wallet");

        } else if (task.getProperty("Payload").startsWith("ROBBERYUPDATE")) {

            String[] payload = task.getProperty("Payload").split("_");
            double old_saldo = saldo;
            saldo = Double.parseDouble(payload[1]);
            if(old_saldo > saldo){
                System.out.println("Inconvenience, soldi: " + saldo);
            }else{
                System.out.println("lucky moment, soldi: " + saldo);
            }
        } else {
            //Se il payload non comincia con YOURTURN devo solo aggiornare il wallet
            String[] s = task.getProperty("Payload").split(",");
            double valore;

            String str1 = s[1]; //cifra 
            String str2 = "null";
            if(!s[2].equals("non_aggiornare_saldo")){
                str2 = s[2]; //saldo conto
            }
            System.out.println(saldo);
            valore = Double.parseDouble(str1);
            if(!str2.equals("null")){
                saldo = Double.parseDouble(str2);
            }
            if(valore < 0){
                wallet = wallet - valore; //se è stato fatto un prelievo "valore" è negativo quindi per essere aggiunto al portafoglio
            }
            //aggiungiamo un meno davanti

            System.out.println("Saldo: " + saldo + "wallet aggiornato: " + wallet);
            if(str2.equals("null")){
                richiestaCompletata = true;
            }
        }

        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);

        conn.send(nmsg2);
        showStatus(nmsg2, true);
    }

    private void updateSaldo(XMLInterface task) {
        String s = task.getProperty("Payload");
        saldo = Double.parseDouble(s);

        System.out.println("Ho ricevuto il saldo: " + saldo);

    }

    private synchronized void moveToSalaSlot() {

        System.out.println("moveToSalaSlot " + Thread.currentThread().getName());
        System.out.println("Stato " + Thread.currentThread().getState());

        while (!inSalaSlot) {

            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_SLOTENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Going to \nrisk my money!";
            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }
        }
    }

    private synchronized void requestSlot() {

        //Dentro OT        
        moveToSalaSlot();

        NotifyMessage nmsg = new NotifyMessage();

        while (richiestaSlot) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        richiestaSlot = true;
        action = "Let's bet!";
        doGiocataSlot();

        while (!nextGiocata && !richiestaSlotCompletata) {
            try {
                System.out.println("\nAspetto...");
                wait(4000);
            } catch (InterruptedException ex) {
            }

        }
        while (nextGiocata) {
//            nextGiocata = false;
            doGiocataSlot();
            try {
                System.out.println("Aspetto per next giocata");
                wait();
            } catch (InterruptedException ex) {
            }

        }

        System.out.println("\nRichiesta completata = " + richiestaSlotCompletata);
        richiestaSlotCompletata = false;
        moveToSlotExit();

    }

    private synchronized void moveToSlot() {

        while (!inSlot) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_SLOT_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            System.out.println("Sono in MoveToSlot");
            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private void doGiocataSlot() {

        moveToSlot();

        double giocata = (Math.floor(((Math.random() * 100) * 100.0) / 100.0));
        while (giocata > wallet) {
            if (wallet == 1) {
                giocata = wallet;
                break;
            }
            giocata = (Math.floor(((Math.random() * 100) * 100.0) / 100.0));
        }
        NotifyMessage nmsg = new NotifyMessage();

        System.out.println("Voglio puntare " + giocata);

        //il cliente invia al supervisor la richiesta di voler giocare
        nmsg.addNext(SUP_6, "BET," + giocata + "," + wallet, "ANY");

        conn.send(nmsg);
        checkStatus(nmsg, true);

    }

    private synchronized void updateSlot(XMLInterface task) {
        String s[] = task.getProperty("Payload").split(",");
        String s1 = s[1]; //wallet

        double cifra = Double.parseDouble(s1);

        NotifyMessage nmsg = new NotifyMessage();
        nmsg.setActivity(task.getProperty("Activity"));
        nmsg.setSerial(task.getProperty("Serial"));
        nmsg.setOutcome("COMPLETED");

        if (cifra > wallet) {
            soldiVinti = Math.floor((soldiVinti + (cifra - wallet)) * 100) / 100;       //soldi vinti in totale
            wallet = Math.floor(cifra * 100) / 100;

            System.out.println("wallet aggiornato: " + wallet + ", soldi vinti: " + soldiVinti);
        } else {
            soldiPersi = Math.floor((soldiPersi + (wallet - cifra)) * 100) / 100; //soldi persi in totale
            wallet = Math.floor(cifra * 100) / 100;

            System.out.println("wallet aggiornato: " + wallet + ", soldi persi: " + soldiPersi);

        }

        double i = Math.round(Math.random() * 100.0) / 100.0;

        if (i < 0.5 && wallet >= 1) {
            System.out.println("\nHo estratto i = " + i + " -> faccio nuova giocata");
            nextGiocata = true;

            conn.send(nmsg);
            checkStatus(nmsg, true);

        } else {

            richiestaSlot = false;
            nextGiocata = false;
            richiestaSlotCompletata = true;
            System.out.println("\nHo estratto i = " + i + " -> non faccio nuova giocata");

            conn.send(nmsg);
            checkStatus(nmsg, true);

        }

        notify();
    }

    //Richiede continuamente di uscire dalle slot finchÃ¨ non raggiunge la porta di uscita
    private synchronized void moveToSlotExit() {

        while (inSalaSlot) {
            NotifyMessage nmsg = new NotifyMessage();
            System.out.println("Richiedo di uscire dalla slot");
            nmsg.addNext(SUP_3, "MOV_SLOTEXIT_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Stop betting!";
            try {
                System.out.println("CLN - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }

        System.out.println("Sono uscito!!!!");

    }

    private void updateAgentMap() {
        String s = agentmap.statsAgent(this);
        JTextArea txt = agentmap.getTxt();
        txt.setText(s);
    }

    //Restituisce variabile agentmap
    public AgentMap getAgentmap() {
        return this.agentmap;
    }

    //Restituisce mappa calpestabile
    public String[][] getMappa_calpestabile() {
        return mappa_calpestabile;
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

    public double getWallet() {
        return wallet;
    }

    @Override
    public String toString() {

        return "Client " + " Action: \n" + action
                + " \nWallet: \n" + String.format("%.2f", wallet)
                + "\nAccount Money :\n" + String.format("%.2f", getSaldo())
                + "\nLost Money :\n" + String.format("%.2f", soldiPersi)
                + "\nWin Money :\n" + String.format("%.2f", soldiVinti)
                + "\nMoney Balance :\n" + String.format("%.2f", (soldiVinti - soldiPersi));

    }

    public void setWallet(double wallet) {
        this.wallet = wallet;
    }

    public double getSaldo() {
        return saldo;
    }

    public boolean isBankOpen() {
        return this.bankOpen;
    }

    public void setBankOpen(boolean bankOpen) {
        this.bankOpen = bankOpen;
    }

}
