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

public class Employee extends Agent implements AgentsIF {

    private AgentType type;

    private AgentMap agentmap;
    private String[][] tiles;
    private String[][] mappa_calpestabile;
    private int clientServed; // numero clienti serviti
    private OperationsThread ot;
    private boolean otRunning;
    private boolean operative;
    private boolean ready;
    private boolean busy;
    private boolean inCaveau;
    private boolean insideCaveau;
    private boolean inDesk;
    private boolean inBank;
    private boolean checked;
    private boolean inWalk;
    private String action;
    private String operationToDo;
    private String opResult;

    public Employee(Connection conn) {
        super(conn);
        this.type = AgentType.EMP;
        this.agentmap = new AgentMap(this);//passo anche l'agente (this) così alla chiusura della finestra posso richiamare il metodo subscribe
        this.agentmap.setType(type.name());
        this.clientServed = 0;
        this.ready = false;
        this.otRunning = false;
        this.operative = false;
        this.busy = false;
        this.inCaveau = false;
        this.insideCaveau = false;
        this.inDesk = false;
        this.operationToDo = null;
        this.checked = false;
        this.opResult = null;
        this.inBank = false;
        this.inWalk = false;
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
            submsg.addActivity(EMP_1);
            submsg.addActivity(EMP_2);
            submsg.addActivity(EMP_3);
            submsg.addActivity(EMP_4);
            submsg.addActivity(EMP_5);
            submsg.addActivity(EMP_6);
            submsg.addActivity(ALARM);
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

        Object lockObject;

        public void run() {

            otRunning = OperationsThread.currentThread().isAlive(); // Setta il flag a true in modo che il thread non venga piÃ¹ avviato
            lockObject = new Object();

            for (;;) {
                System.out.println("OT" + Thread.currentThread().getName());
                moveToBank();
                notOperative();
                nextAgent();
                askToSupervisor();
                waitForCheck();
                completeOperation();

            }
        }

    }
    //Thread per  richieste di vicinato

    /**
     * Run del thread dell'impiegato (start dell'agent dal main)
     */
    @Override
    public void run() {

        Thread.currentThread().setName("CT_" + getType() + "_" + getConn().getLogin());

        new PresenceThread().start(); //thread parallelo che segnala la presenza continuamente

        reqNeigh();

        ot = new OperationsThread(); //istanzio ma non AVVIO subito
        ot.setName("OT_" + getType() + "_" + getConn().getLogin());

        // Ciclo perenne. Recupera le activities da svolgere e le svolge
        for (;;) {

            if (!otRunning && ready) {
                ot.start();
            }
            EngageMessage engmsg = new EngageMessage();

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

                if (EMP_1.equals(activity)) {

                    System.out.println("Voglio muovermi \n");
                    movement(task);
                    updateAgentMap();

                } else if (EMP_2.equals(activity)) {
                    try {
                        //In risposta al circondario inviato dal supervisor si esegue questo metodo
                        doNeigh(task);
                    } catch (IOException ex) {
                        Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (EMP_3.equals(activity)) {

                    doOperation(task);
                    updateAgentMap();

                } else if (EMP_4.equals(activity)) {

                    doCheck(task);
                    updateAgentMap();

                } else if (EMP_5.equals(activity)) {

                    System.out.println("Esito aggiornamento posizione");
                    updatePos(task);
                    updateAgentMap();

                } else if (EMP_6.equals(activity)) {

                    System.out.println("E' il turno di");
                    getTurn(task);
                    updateAgentMap();

                } else if (ALARM.equals(activity)) {

                    NotifyMessage nmsg2;
                    nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                            task.getProperty("Serial"),
                            "COMPLETED",
                            null);

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
            }
        }
    }

    private void movement(XMLInterface task) {
        System.out.println("movement" + Thread.currentThread().getName());
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

    private synchronized void getTurn(XMLInterface task) {
        System.out.println("getTurn" + Thread.currentThread().getName());
        NotifyMessage nmsg = new NotifyMessage();
        String[] payload = task.getProperty("Payload").split("_");

        if (payload[0].equals("DONE")) {
            System.out.println(payload[1]);
            nmsg.addNext(CLN_3, "YOURTURN_" + conn.getLogin(), payload[1]);
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");
            conn.send(nmsg);
            busy = true;
            action = "Waiting for\n" + payload[1] + "!";

        } else if (payload[0].equals("NOBODY")) {

            System.out.println("NOBODY");

            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");
            conn.send(nmsg);
            busy = false;

        }

        notify();
    }

    // Metodo che restituisce le posizioni delle tile non calpestabili al netto di quella in cui Ã¨ presente l'agente corrente
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

    //Se ricevo messaggio di aggiornamento effettuato sveglio il thread neighborhood per effettuare richiesta vicinato
    private void updatePos(XMLInterface task) {
        System.out.println("updatePos" + Thread.currentThread().getName());
        String[] payload = task.getProperty("Payload").split("_");
        NotifyMessage nmsg2;
        nmsg2 = new NotifyMessage(task.getProperty("Activity"),
                task.getProperty("Serial"),
                "COMPLETED",
                null);

        System.out.println("OK - Sveglio neighborhood");
        conn.send(nmsg2);

        if (payload[0].equals("UPDATEDPOS")) {

            if (payload.length == 3) {

                if (payload[1].equals("LAST") && payload[2].equals("DESKEMP")) {

                    if (!operative) {
                        switchOperative(true);
                    }

                    inDesk = true;

                } else if (payload[1].equals("LAST") && payload[2].equals("CAVEAUENTRY")) {

                    inCaveau = true;

                } else if (payload[1].equals("LAST") && payload[2].equals("REACHGOLD")) {

                    System.out.println("Sono nel caveau");
                    insideCaveau = true;

                } else if (payload[1].equals("LAST") && payload[2].equals("CAVEAUEXIT")) {

                    inCaveau = false;
                    insideCaveau = false;

                } else if (payload[1].equals("LAST") && payload[2].equals("BANKENTRY")) {

                    inBank = true;
                } else if (payload[1].equals("LAST") && payload[2].equals("WALKING")) {

                    System.out.println("Ho finito la passeggiata");
                    inWalk = true;
                }
            }

            reqNeigh();

        }
    }

    private void reqNeigh() {
        NotifyMessage nmsg = new NotifyMessage();
        nmsg.addNext(SUP_1, getType().toString(), "ANY");
        conn.send(nmsg);
        checkStatus(nmsg, true);
    }

//mette impiegato in stato operativo o no
    private void switchOperative(boolean b) {
        this.operative = b;
    }

    //metodo che, ricevuto il circondario dal supervisor, setta le tile e la mappa calpestabile in questa classe e nella classe che crea la gui 
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

    private synchronized void moveToBank() {
        //Se agente non è già in banca si muove verso la porta

        while (!inBank) {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_BANKENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Let's go\nto work!";
            try {
                System.out.println("EMP - Mi sto muovendo in banca ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    //metodo che fa raggiungere il desk all'impiegato se non operativo
    private synchronized void notOperative() {
        System.out.println("notOperative" + Thread.currentThread().getName());

        while (!operative) {
            System.out.println("EMP - Non ancora operativo");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_DESK_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            try {
                System.out.println("EMP - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    //Impiegato chiede al supervisor di chi è il turno
    private synchronized void nextAgent() {
        System.out.println("nextAgent " + Thread.currentThread().getName());
        System.out.println("Class " + this.getClass());
        while (!busy) {

            if (clientServed != 0) {
                walking();
                returnToDesk();
            }
            System.out.println("EMP - Chiedo chi è il prossimo");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_7, "GIVEME_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Waiting for\na client!";

            try {
                System.out.println("EMP - Metto OT in wait - NextAgent");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private void shutdown() {

        subscribe(true);
        conn.terminate(); // stops only this agent (all agents share the same JVM)
    }

    private synchronized void returnToDesk() {
        System.out.println("returnToDesk" + Thread.currentThread().getName());
        while (!inDesk) {
            System.out.println("EMP - Ritorna al desk");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_DESK_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Back to \nmy desk!";
            try {
                System.out.println("EMP - Metto OT in wait per tornare al desk");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private synchronized void askToSupervisor() {
        System.out.println("askToSupervisor" + Thread.currentThread().getName());

        while (operationToDo == null) {
            reqNeigh();
            System.out.println("ho chiesto il vicinato da fermo al desk!!!!");
            try {
                wait();
            } catch (InterruptedException ex) {
            }
            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        moveToCaveau();
        moveInsideCaveau();
        System.out.println("Operation to do: " + operationToDo);

        NotifyMessage nmsg = new NotifyMessage();
        nmsg.addNext(SUP_4, operationToDo, "ANY");

        conn.send(nmsg);
        showStatus(nmsg, true);
    }

    private synchronized void moveToCaveau() {
        System.out.println("moveToCaveau" + Thread.currentThread().getName());

        while (!inCaveau) {

            System.out.println("EMP - Non ancora in caveau. Richiedo spostamento");

            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_CAVEAUENTRY_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Going to \ncaveau!";

            inDesk = false;

            System.out.println("EMP - Metto OT in wait ");
            try {
                wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(Employee.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    private synchronized void moveInsideCaveau() {
        System.out.println("moveInsideCaveau" + Thread.currentThread().getName());

        while (!insideCaveau) {
            System.out.println("EMP - Non ancora dentro al caveau. Richiedo spostamento");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_INSIDECAVEAU_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Wow, so much\ngold!";
            try {
                System.out.println("EMP - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private synchronized void moveCaveauExit() {
        System.out.println("moveCaveauExit" + Thread.currentThread().getName());
        while (insideCaveau) {
            System.out.println("EMP - Non ancora uscito dal caveau. Richiedo spostamento");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_CAVEAUEXIT_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Leaving \ncaveau!";
            try {
                System.out.println("EMP - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }
        }

    }

    private void doOperation(XMLInterface task) {
        System.out.println("doOperation" + Thread.currentThread().getName());

        String[] s = task.getProperty("Payload").split(",");
        NotifyMessage nmsg = new NotifyMessage();

        String str1 = s[1]; //cifra da prelevare o depositare
        String str2 = s[2]; //cifra nel wallet
        String str3 = s[3]; //id Agente 

        operationToDo = "OP," + str1 + "," + str2 + "," + str3;

        nmsg.setActivity(task.getProperty("Activity"));
        nmsg.setSerial(task.getProperty("Serial"));
        nmsg.setOutcome("COMPLETED");

        conn.send(nmsg);
        checkStatus(nmsg, true);

    }

    private void waitForCheck() {
        System.out.println("Wait for check" + Thread.currentThread().getName());

        synchronized (ot.lockObject) {
            while (!checked) {

                try {
                    System.out.println("EMP - Metto OT in wait per il check ");

                    ot.lockObject.wait();
                } catch (InterruptedException ex) {
                }
            }
        }

    }

    private synchronized void completeOperation() {
        System.out.println("completeOperation" + Thread.currentThread().getName());
        
        String[] s = opResult.split(",");
        
        NotifyMessage nmsg = new NotifyMessage();
        nmsg.addNext(CLN_3, s[0] + "," + 0 + "," + s[2], s[3]);
        conn.send(nmsg);
        checkStatus(nmsg, true);

        moveCaveauExit();
        returnToDesk();
        
        NotifyMessage nmsg1 = new NotifyMessage();
        nmsg1.addNext(CLN_3, s[0] + "," + s[1] + "," + "non_aggiornare_saldo", s[3]);
        conn.send(nmsg1);
        checkStatus(nmsg1, true);

        

        clientServed = clientServed + 1;
        opResult = null;
        checked = false;
        operationToDo = null;
        busy = false;
        updateAgentMap();

    }

    private void doCheck(XMLInterface task) {
        System.out.println("doCheck" + Thread.currentThread().getName());
        String[] s = task.getProperty("Payload").split(",");
        NotifyMessage nmsg = new NotifyMessage();

        String str1 = s[1]; //cifra da prelevare o depositare
        String str2 = s[2]; //saldo
        String str3 = s[3]; //id cliente

        opResult = "CHECK," + str1 + "," + str2 + "," + str3;
        nmsg.setActivity(task.getProperty("Activity"));
        nmsg.setSerial(task.getProperty("Serial"));
        nmsg.setOutcome("COMPLETED");
        //nmsg.addNext(CLN_3, "CHECK," + str1 + "," + str2, str3);

        conn.send(nmsg);
        checkStatus(nmsg, true);
        //notifyAll();
        checked = true;

        synchronized (ot.lockObject) {
            ot.lockObject.notify();
        }

    }

    private void updateAgentMap() {
        String s = agentmap.statsAgent(this);
        JTextArea txt = agentmap.getTxt();
        txt.setText(s);
    }

    //Passeggiata
    private synchronized void walking() {
        inDesk = false;
        while (!inWalk) {

            System.out.println("Sto passeggiando");
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.addNext(SUP_3, "MOV_WALKING_" + getType(), "ANY");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            action = "Let's take\na rest!";
            try {
                System.out.println("EMP - Metto OT in wait ");
                wait();
            } catch (InterruptedException ex) {
            }

        }
        System.out.println("Sto uscendo da walking");
        inWalk = false;
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

    public int getClientServed() {
        return clientServed;
    }

    @Override
    public String toString() {

        String operationFormat = null;
        String[] operationToDoSplit;
        if (operationToDo != null) {
            operationToDoSplit = operationToDo.split(",");

            if (Double.parseDouble(operationToDoSplit[1]) > 0) {
                operationFormat = "Lodge: " + operationToDoSplit[1] + "\n";
            } else {
                operationFormat = "Withdraw: " + operationToDoSplit[1] + "\n";
            }
            operationFormat = operationFormat + "Id Agent: " + operationToDoSplit[3];

            System.out.println("operation to do: " + operationFormat);
        }
        return "Employee " + "Action: \n" + action
                + (operationFormat == null ? "" : "\nOperation to Do: \n" + operationFormat)
                + "\nClient Served: \n" + clientServed;
    }

}
