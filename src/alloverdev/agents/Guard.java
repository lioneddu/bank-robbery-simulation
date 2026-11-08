package alloverdev.agents;

import alloverdev.astar.AStar;
import alloverdev.interfaces.AgentsIF;
import alloverdev.maps.AgentMap;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextArea;
import alloverdev.broker.*;

public class Guard extends Agent implements AgentsIF {

    private AgentType type;
    private AgentMap agentmap;
    private String[][] tiles;
    private String[][] mappa_calpestabile;
    private double moneyRecovered; //denaro requisito ai ladri 
    private int killedThief; //numero di ladri uccisi
    private OperationsThread ot;
    private boolean otRunning;
    private boolean operative;
    private boolean inCage;
    private boolean inDesk;
    private boolean alarm;
    private boolean fineRonda;
    private boolean ready;
    private String action;
    private boolean ladroCatturato;
    private boolean inBancomat;
    private String idThief;

    public Guard(Connection conn) {
        super(conn);
        this.type = AgentType.GRD;
        this.agentmap = new AgentMap(this); //passo anche l'agente (this) così alla chiusura della finestra posso richiamare il metodo subscribe
        this.agentmap.setType(type.name());
        this.moneyRecovered = 0.0;
        this.killedThief = 0;
        this.ready = false;
        this.otRunning = false;
        this.operative = false;
        this.inCage = false;
        this.inDesk = false;
        this.alarm = false;
        this.fineRonda = false;
        this.ready = false;
        this.action = "";
        this.ladroCatturato = false;
        this.inBancomat = false;
        this.idThief = null;
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
            submsg.addActivity(GRD_1);
            submsg.addActivity(GRD_2);
            submsg.addActivity(GRD_3);
            submsg.addActivity(GRD_5);
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

    private class OperationsThread extends Thread {

        public void run() {
            otRunning = Thread.currentThread().isAlive(); // Setta il flag a true in modo che il thread non venga piÃ¹ avviato

            for (;;) {

                moveDesk();
                moveRonda();

                catchThief();

                moveToBancomat();

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
     * Run del thread della guardia (start dell'agent dal main)
     */
    @Override
    public void run() {

        new PresenceThread().start(); //thread parallelo che segnala la presenza continuamente
        Thread.currentThread().setName("CT_" + getType() + "_" + getConn().getLogin());
        reqNeigh();
        // Ciclo perenne. Recupera le activities da svolgere e le svolge

        ot = new OperationsThread(); //istanzio ma non AVVIO subito
        ot.setName("OT_" + getType() + "_" + getConn().getLogin());

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

                if (GRD_1.equals(activity)) {

                    System.out.println("Voglio muovermi \n");
                    movement(task);
                    updateAgentMap();

                } else if (GRD_2.equals(activity)) {
                    try {
                        //In risposta al circondario inviato dal supervisor si esegue questo metodo
                        doNeigh(task);
                        updateAgentMap();
                    } catch (IOException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (GRD_3.equals(activity)) {

                    System.out.println("Vai a catturare il ladro!");
                    startChasing(task);

                } else if (GRD_5.equals(activity)) {

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

    private synchronized void movement(XMLInterface task) {
        NotifyMessage nmsg = new NotifyMessage();

        String nick = task.getProperty("Scheduler"); //idAgent per get nel glass

        String[] payload = task.getProperty("Payload").split("_");

        if (payload[0].equals("APPROVEDMOV")) { //movimento approvato

            String[] pos = payload[1].split(",");

            int y1 = (Integer.parseInt(pos[1]));
            int x1 = (Integer.parseInt(pos[0]));
            System.out.println("Dest x1 " + x1 + ", Dest y1 " + y1);

            int[][] cleanedNotWalkableTiles2 = getCleanedNotWalkableTiles();

            AStar aStar1 = new AStar(mappa_calpestabile.length, mappa_calpestabile.length, mappa_calpestabile.length / 2, mappa_calpestabile.length / 2, y1, x1, cleanedNotWalkableTiles2);
            aStar1.display();
            aStar1.process();

            aStar1.displaySolution();

            List pathList = aStar1.getPathList();
            System.out.println("PathList:" + pathList);

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

        //Uso la size per creare un vettore piÃ¹ piccolo di uno rispetto ad essa
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

    private synchronized void updatePos(XMLInterface task) {

        String[] payload = task.getProperty("Payload").split("_");
        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);

        conn.send(nmsg2);

        if (payload[0].equals("UPDATEDPOS")) {
            //Se mi è stata aggiornata la posizione
            if (payload.length == 3) {
                // Controllo se ho raggunto la posizione finale (tramite keywords inviate dal supervisor)
                if (payload[1].equals("LAST") && payload[2].equals("CAGEENTRY")) {
                    System.out.println("InCage");
                    inCage = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("CAGEDESK")) {
                    System.out.println("InDesk");
                    inDesk = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("CAGEEXIT")) {
                    System.out.println("outCage");
                    inCage = false;
                } else if (payload[1].equals("LAST") && payload[2].equals("RONDA")) {
                    System.out.println("Ronda");
                    fineRonda = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("BANCOMAT")) {
                    System.out.println("Bancomat");
                    inBancomat = true;
                }
            }

            reqNeigh();

        }
    }

    private String[][] getNeighMov() {

        String str[][] = new String[mappa_calpestabile.length][mappa_calpestabile.length];

        for (int i = 0; i < mappa_calpestabile.length; i++) {
            for (int j = 0; j < mappa_calpestabile.length; j++) {
                str[i][j] = i + "," + j;
            }
        }
        return str;
    }

    private synchronized void doNeigh(XMLInterface task) throws IOException {

//        System.out.println();
//        System.out.println("Il Payload ricevuto e': ");
//        System.out.println(task.getProperty("Payload"));
//        System.out.println();
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

    private synchronized void moveEntryCage() {

        while (!inCage && !alarm) {
            System.out.println("GRD - Non ancora operativa");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_CAGEENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Going to \npolice station!";

            try {
                System.out.println("GRD - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        //inCage = false;
    }

    private synchronized void moveDesk() {
        moveEntryCage();
        while (!inDesk && !alarm) {
            System.out.println("GRD - Movimento desk");
            NotifyMessage nmsg2 = new NotifyMessage();
            nmsg2.addNext(SUP_3, "MOV_CAGEDESK_" + getType(), "ANY");
            conn.send(nmsg2);
            checkStatus(nmsg2, true);
            action = "Going to \nmy desk!";
            try {
                System.out.println("GRD - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }

    }

    private synchronized void moveRonda() {
        moveUscita();
        inDesk = false;

        while (!fineRonda && !alarm) {
            System.out.println("GRD - Movimento desk");
            NotifyMessage nmsg3 = new NotifyMessage();
            nmsg3.addNext(SUP_3, "MOV_RONDA_" + getType(), "ANY");
            conn.send(nmsg3);
            checkStatus(nmsg3, true);
            action = "Let's watch \nover the streets!";

            try {
                System.out.println("GRD - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        fineRonda = false;
    }

    private synchronized void moveUscita() {

        while (inCage && !alarm) {
            System.out.println("GRD - Movimento desk");
            NotifyMessage nmsg2 = new NotifyMessage();
            nmsg2.addNext(SUP_3, "MOV_CAGEEXIT_" + getType(), "ANY");
            conn.send(nmsg2);
            checkStatus(nmsg2, true);
            action = "Let's get out\nof here!";
            try {
                System.out.println("GRD - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }

    }

    //Inizio inseguimento ladro 
    private void startChasing(XMLInterface task) {

        String[] payload = task.getProperty("Payload").split("_");
        if (payload[1].equals("STOP")) {
            //Alarm false sia per il messaggio "ALARM_STOP" che "ALARM_STOP_CATCHED"
            alarm = false;

        } else {

            idThief = payload[1];
            alarm = true;
            System.out.println("Allarme attivato: ladro da inseguire: " + idThief);
        }
        
        if (payload.length == 3) {
            if (payload[2].startsWith("CATCHED")) {

                ladroCatturato = true;
                this.killedThief = this.killedThief + 1;
                System.out.println("ladroCatturato: " + ladroCatturato);
                System.out.println(this.moneyRecovered);
                this.moneyRecovered += Double.parseDouble(payload[2].substring(7, payload[2].length()));
                this.moneyRecovered = Math.floor(this.moneyRecovered *100)/100;
                System.out.println("Soldi da restituire:" + this.moneyRecovered);
                
                //Avviso il supervisor che sono di nuovo disponibile
                NotifyMessage nmsg2 = new NotifyMessage();
                nmsg2.addNext(SUP_3, "ADDGRD_" + getType(), "ANY");
                conn.send(nmsg2);
                checkStatus(nmsg2, true);
                System.out.println("Messaggio ADDGRD inviato");
            }
        }
        NotifyMessage nmsg = new NotifyMessage();
        nmsg = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);
        conn.send(nmsg);
        checkStatus(nmsg, true);
    }

    private synchronized void catchThief() {
        inDesk = false;
        inCage = false;

        while (alarm) {
            System.out.println("GRD - movimento verso ladro");
            NotifyMessage nmsg2 = new NotifyMessage();
            nmsg2.addNext(SUP_3, "MOV_" + "THIEF" + idThief + "_" + getType(), "ANY");
            conn.send(nmsg2);
            checkStatus(nmsg2, true);
            action = "Chasing \n" + idThief + "!";
            try {
                System.out.println("GRD - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private synchronized void moveToBancomat() {
        if (ladroCatturato) {
            moveEntryCage();
            while (!inBancomat && !alarm) {
                System.out.println("GRD - movimento verso bancomat");
                NotifyMessage nmsg2 = new NotifyMessage();
                nmsg2.addNext(SUP_3, "MOV_BANCOMAT_" + getType(), "ANY");
                conn.send(nmsg2);
                checkStatus(nmsg2, true);
                action = "Going \nto bancomat!";
                try {
                    System.out.println("GRD - Metto OT in wait ");
                    wait();
                } catch (InterruptedException ex) {
                }
            }
            if (inBancomat) {
                NotifyMessage nmsg3 = new NotifyMessage();
                nmsg3.addNext(SUP_4, "GRD," + this.moneyRecovered, "ANY");
                conn.send(nmsg3);
                checkStatus(nmsg3, true);
                action = "Sending \nmoney back!";
                this.moneyRecovered = 0.0;

                ladroCatturato = false;
                inBancomat = false;
            }
        }
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

    public double getMoneyRecovered() {
        return moneyRecovered;
    }

    public int getKilledThief() {
        return killedThief;
    }

    @Override
    public String toString() {
        return "Guard " + "Action: \n" + action + 
                " \nMoney Recovered: \n" + String.format("%.2f", moneyRecovered) + 
                "\nFoiled Robbery: \n" + killedThief;
    }

}
