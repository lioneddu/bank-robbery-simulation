package alloverdev.agents;

import alloverdev.operation.Slot;
import alloverdev.operation.Robbery;
import alloverdev.operation.Bank;
import alloverdev.operation.Account;
import alloverdev.astar.AStar;
import alloverdev.interfaces.AgentsIF;
import alloverdev.maps.MapMain;
import java.awt.Image;
import java.awt.Dimension;
import java.awt.Toolkit;
import static java.lang.Thread.sleep;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import alloverdev.broker.*;

public class Supervisor extends Agent implements AgentsIF {

    private AgentType type;
    private final int SCANNER_SLEEP = 10000;
    private AgentList activeAgents;
    private MapMain mapMain; //Mappa generale
    private Bank b;
    private boolean running;
    private Slot slot;
    private ArrayList<String> bankTurn;
    private HashMap<String, int[]> requested_coord; //HashMap con id agente e le coordinate delle richieste di movimento (per gestione collisioni)
    private List<Robbery> robberies; //Lista rapine
    private LinkedList<String> guardList; // Lista guardie disponibili per una cattura
    private HashMap<String, int[]> thiefList; //Lista ladri e loro posizioni (da fornire a guardia)
    private boolean generalAlarm; // allarme generale = true se una rapina è in corso
    private double stealedMoney; // viene sovrascritto ad ogni furto
    private HashMap<String, String[]> FIXEDDEST; //HashMap con chiave id agente e valore posizione in cui si vuole spostare in maniera randomica (passeggiate, ronda ecc.)

    // ===== VETTORI POSIZIONI =====
    private final String[] BANK_POS_ENTRY, BANK_POS_ENTRY_RES, BANK_POS_EXIT, BANK_POS_EXIT_RES,
            DESK_CAGE_POS, RONDA_POS, CAGE_POS_ENTRY, CAGE_POS_EXIT, BANCOMAT_POS,
            SALA_SLOT_POS_ENTRY, SALA_SLOT_POS_EXIT, SLOT_POS,
            POORGRASS_POS, GRASS_POS, BUSH_POS,
            DESK_POS, GOLD_POS,
            CAVEAU_POS_ENTRY, CAVEAU_POS_EXIT,
            BANKPAVEMENT_POS, TICKET_POS,
            ALARMC_POS, ALARMBL_POS, ALARMBR_POS;

    private AlarmThread alarmThread;

    /**
     * Agente Supervisor che gestice l'interazione con gli agenti e il gioco
     *
     * @param conn rappresenta la connessione al broker
     * @param circondario rappresenta dimensione circondario (default = 5)
     */
    public Supervisor(Connection conn, int circondario) {

        super(conn);
        this.type = AgentType.SUP;
        this.mapMain = new MapMain(this, circondario);
        this.requested_coord = new HashMap<>();
        this.guardList = new LinkedList<>();
        // mappa composta da id Agente - Data ultima comunicazione posizione
        activeAgents = new AgentList();
        b = new Bank();
        running = true;
        robberies = new ArrayList<>();
        slot = new Slot();
        bankTurn = new ArrayList<String>();
        this.FIXEDDEST = new HashMap<>();
        this.generalAlarm = false;
        this.stealedMoney = 0;
        this.thiefList = new HashMap<>();
        this.FIXEDDEST = new HashMap<>();

        BANK_POS_ENTRY = mapMain.getG().getTilePos("32").split("-"); //Porte ingresso Banca ING per CLN e THF
        BANK_POS_EXIT = mapMain.getG().getTilePos("100").split("-"); //Porte uscita Banca per CLN e THF
        BANK_POS_ENTRY_RES = mapMain.getG().getTilePos("101").split("-"); //Porte ingresso Banca ING per EMP e GRD
        BANK_POS_EXIT_RES = mapMain.getG().getTilePos("102").split("-"); //Porte uscita Banca per EMP e GRD
        SALA_SLOT_POS_ENTRY = mapMain.getG().getTilePos("21").split("-"); //Porte sala slot ING
        SALA_SLOT_POS_EXIT = mapMain.getG().getTilePos("103").split("-"); //Porte sala slot uscita
        SLOT_POS = mapMain.getG().getTilePos("90").split("-"); //Slot 1 - 2 - 3
        CAGE_POS_ENTRY = mapMain.getG().getTilePos("34").split("-"); //Porte Caserma ING
        CAGE_POS_EXIT = mapMain.getG().getTilePos("104").split("-"); //Porte Caserma uscita
        DESK_CAGE_POS = mapMain.getG().getTilePos("83").split("-"); //Sedia Caserma 1 - 2 - 3 - 4
        DESK_POS = mapMain.getG().getTilePos("76").split("-"); //Sedia desk 1 - 2 - 3 - 4 -5
        GOLD_POS = mapMain.getG().getTilePos("84").split("-"); //Posizioni per lingotti 1 - 2 - 3
        RONDA_POS = mapMain.getG().getTilePos("68").split("-"); // Posizioni ronda 1 - 2 -3 - 4 - 5
        CAVEAU_POS_ENTRY = mapMain.getG().getTilePos("33").split("-"); //PORTE CAVEAU INGRESSO
        CAVEAU_POS_EXIT = mapMain.getG().getTilePos("105").split("-"); //PORTE CAVEAU USCITA
        POORGRASS_POS = mapMain.getG().getTilePos("44").split("-");
        GRASS_POS = mapMain.getG().getTilePos("120").split("-"); //posizioni tile ghiaia
        TICKET_POS = mapMain.getG().getTilePos("106").split("-");
        BANKPAVEMENT_POS = mapMain.getG().getTilePos("107").split("-");
        BUSH_POS = mapMain.getG().getTilePos("29").split("-");
        ALARMC_POS = mapMain.getG().getTilePos("72").split("-");
        ALARMBL_POS = mapMain.getG().getTilePos("1").split("-");
        ALARMBR_POS = mapMain.getG().getTilePos("2").split("-");
        BANCOMAT_POS = mapMain.getG().getTilePos("130").split("-");

        //Ricavo posizione davanti al bancomat perchè sia usata come destinazione
        for (int i = 0; i < BANCOMAT_POS.length; i++) {
            String[] b = BANCOMAT_POS[i].split(",");
            int newPos1 = Integer.parseInt(b[1]) - 1;
            BANCOMAT_POS[i] = b[0] + "," + newPos1;
            System.out.println(BANCOMAT_POS[i]);
        }

    }

    /*
    *Metodo per restituire la connection al broker
     */
    public Connection getConn() {
        return conn;
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
            submsg.addActivity(SUP_1);
            submsg.addActivity(SUP_2);
            submsg.addActivity(SUP_3);
            submsg.addActivity(SUP_4);
            submsg.addActivity(SUP_6);

            submsg.addActivity(SUP_7);
        }
        conn.send(submsg);
        showStatus(submsg, true);
    }

    /**
     * Thread implicato nella scannerizzazione degli agenti attivi in un
     * determinato momento Se un agente non comunica la sua presenza da più di
     * 20s viene rimosso
     */
    private class ScannerThread extends Thread {

        public void run() {
            while (true) {
                try {
                    sleep(SCANNER_SLEEP);
                } catch (Exception ex) {
                }
                if (!activeAgents.getList().isEmpty()) {
                    for (String s : activeAgents.getList().keySet()) {
                        if (activeAgents.getDate(s).getTime() < new Date().getTime() - 3 * SCANNER_SLEEP) {
                            removeAgent(s);
                        }

                    }
                } else if (activeAgents.getList().isEmpty() && !isRunning()) {
                    shutdown();
                }
            }
        }

        /**
         * Metodo che esegue la rimozione dell'agente, chiamato da ScannerThread
         *
         * @param s Stringa che rappresenta l'id dell'agente (del tipo
         * 'CLxxxxxx')
         */
        private void removeAgent(String s) {

            //Se è una guardia viene rimossa anche dal vettore delle guardie disponibili
            if (guardList.contains(s)) {
                guardList.remove(s);
            }
            //Aggiorno mappa
            updateAgentPos(s); //repaint interno

//            System.out.println("Dopo update agent");
//            printMappaCalpestabile();
            //rimuovo dalla hashMap del supervisor
            activeAgents.removeAgent(s);

            //se cliente, rimuovo il conto dalla banca
            if (b.getAccounts().containsKey(s)) {
                System.out.println("Prima" + b.getAccounts());
                b.removeAccount(s);
                System.out.println("Dopo" + b.getAccounts());
            }

            //se l'impiegato viene eliminato, si elimina anche dalla lista empDesk
            if (activeAgents.getEmpDeskList().containsKey(s)) {

                System.out.println("\nPrima " + activeAgents.getEmpDeskList());
                activeAgents.getEmpDeskList().remove(s);
                System.out.println("Dopo " + activeAgents.getEmpDeskList());

                if (activeAgents.getEmpDeskList().isEmpty()) {
                    System.out.println("Chiudo la banca!");
                    b.setOpen(false);
                }
            }

            //rimuovo dal Glass e ridisegno       
            mapMain.getGlass().removeAgent(s); //repaint interno

            //Rimuovo da requested_coord
            requested_coord.remove(s);
        }

    }

    /**
     * Metodo che effettua l'aggiornamento della mappa calpestabile a seguito
     * della modifica della posizione di un agente, della sua rimozione o
     * aggiunta
     *
     * @param s Username dell'agente o id del tipo 'CLxxxxxxx'
     */
    private void updateAgentPos(String s) {
        HashMap<String, int[]> pos = mapMain.getGlass().getPosizioni();
        int[] p = pos.get(s);
        if (p != null) {
            //Si setta 1 al posto del type (CLN,EMP,THF ... ) nella mappa calpestabile dove prima era presente un agente (2 se era una casella di spawn)
            int flag = 0;
            for (int i = 0; i < mapMain.getG().getCaselle_spawn().size(); i++) {
                if (mapMain.getG().getCaselle_spawn().get(i)[0] * 24 == p[1] && mapMain.getG().getCaselle_spawn().get(i)[1] * 24 == p[0]) {
                    mapMain.getG().setMappa_calpestabile("2", p[0] / mapMain.getG().getTileWidth() + mapMain.getG().getDim_circondario() / 2, p[1] / mapMain.getG().getTileHeight() + mapMain.getG().getDim_circondario() / 2);
                    flag = 1;
                    mapMain.getGlass().setSpawn_occupati(i, Boolean.FALSE);
                }
            }
            if (flag == 0) {
                mapMain.getG().setMappa_calpestabile("1", p[0] / mapMain.getG().getTileWidth() + mapMain.getG().getDim_circondario() / 2, p[1] / mapMain.getG().getTileHeight() + mapMain.getG().getDim_circondario() / 2);
            }

            mapMain.getGlass().repaint();
        } else {
            System.err.println("Posizione agente nulla");
        }
    }

    /**
     *
     * Run del thread del supervisor (start dell'agent dal main)
     */
    @Override
    public void run() {

        //Si fa partire lo scannerThread per tenere sotto controllo gli agenti
        new ScannerThread().start();
        alarmThread = new AlarmThread(); //Avviamo ma va subito in wait
        alarmThread.start();

        // Ciclo perenne per recuperare e svolgere le activity
        for (;;) {

            if (!isRunning() && activeAgents.getList().isEmpty()) {
                System.exit(0);
            }

            EngageMessage engmsg = new EngageMessage();

            //====================================
            conn.send(engmsg, true);
            checkStatus(engmsg, true);

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

                if (SUP_1.equals(activity)) {

                    System.out.println("SUP - Richiesta vicinato\n");
                    doPresence(task);

                } else if (SUP_2.equals(activity)) {

                    System.out.println("SUP - Ricevuta Presenza!!\n");
                    agentIsAlive(task);

                } else if (SUP_3.equals(activity)) {

                    System.out.println("SUP - Richiesta di Movimento\n");
                    doMove(task);

                } else if (SUP_4.equals(activity)) {

                    System.out.println("SUP - Richiesta Operazione Bancaria\n");
                    doBankingOperation(task);

                } else if (SUP_6.equals(activity)) {

                    System.out.println("SUP - Slot Operation");
                    doSlotOperation(task);

                } else if (SUP_7.equals(activity)) {

                    System.out.println("SUP - Gestione Coda in Banca");
                    manageBankQueue(task);

                }
            }

        }

    }

    /**
     * Metodo che aggiorna le date degli agenti attivi per considerarli ancora
     * attivi
     *
     * @param task messaggio ricevuto
     */
    private void agentIsAlive(XMLInterface task) {

        activeAgents.addAgent(task.getProperty("Scheduler"));
        NotifyMessage nmsg2 = new NotifyMessage();
        nmsg2.setActivity(task.getProperty("Activity"));
        nmsg2.setSerial(task.getProperty("Serial"));
        nmsg2.setOutcome("COMPLETED");
        conn.send(nmsg2, true);
        showStatus(nmsg2, true);
        System.out.println("ricevuta la presenza di: " + task.getProperty("Scheduler") + " alle ore: " + new Date());

        // Se il cliente è creato per la prima volta creao il suo conto bancario 
        if (task.getProperty("Payload").equals("CLN")) {
            if (activeAgents.getList().containsKey(task.getProperty("Scheduler"))) {
                Account c;
                c = b.getConto(task.getProperty("Scheduler"));
                if (c == null) {
                    b.addAccount(task.getProperty("Scheduler"));
                    c = b.getConto(task.getProperty("Scheduler"));
                    String s = Double.toString(c.getSaldo());
                    NotifyMessage nmsg1 = new NotifyMessage();
                    nmsg1.addNext(CLN_4, s, task.getProperty("Scheduler"));
                    conn.send(nmsg1);
                    showStatus(nmsg1, true);
                } else {
                    System.out.println("il conto del cliente " + task.getProperty("Scheduler") + " esiste già! Conto: " + c.getSaldo());
                }
            }

        }
    }

    /*
    *Metodo per gestire le code in banca
    *@param task messaggio ricevuto
     */
    private void manageBankQueue(XMLInterface task) {

        NotifyMessage nmsg = new NotifyMessage();
        String payload = task.getProperty("Payload");
        String[] action = payload.split("_");

        if (action[0].equals("INSERTME")) {
            //Agente chiede di essere inserito nella coda in Banca
            activeAgents.addToBankQueue(task.getProperty("Scheduler"));

            //nmsg.addNext(get_activity_updatepos(action[1]), "UPDATEDPOS_", task.getProperty("Scheduler"));
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");

        } else if (action[0].equals("GIVEME")) {
            //Impiegato chiede di ricevere il nick dell'agente in testa alla coda
            if (activeAgents.getBankQueueSize() > 0) {

                nmsg.addNext(EMP_6, "DONE_" + activeAgents.removeFromBankQueue(), task.getProperty("Scheduler"));
                nmsg.setActivity(task.getProperty("Activity"));
                nmsg.setSerial(task.getProperty("Serial"));
                nmsg.setOutcome("COMPLETED");

            } else {
                //Nessuno in coda
                nmsg.addNext(EMP_6, "NOBODY_", task.getProperty("Scheduler"));
                nmsg.setActivity(task.getProperty("Activity"));
                nmsg.setSerial(task.getProperty("Serial"));
                nmsg.setOutcome("COMPLETED");
            }
        }
        conn.send(nmsg, true);
        showStatus(nmsg, true);
    }

    /**
     * Metodo per effettuare i movimenti (activity REQMOV)
     *
     * @param task messaggio ricevuto
     */
    private void doMove(XMLInterface task) {

        NotifyMessage nmsg = new NotifyMessage();
        //Recupero posizione agente
        int[] agentPos = mapMain.getGlass().getPosizione(task.getProperty("Scheduler"));

        System.out.println("agentPos[0]: " + agentPos[0]);
        System.out.println("agentPos[1]: " + agentPos[1]);

        int agentPosX = agentPos[0] / this.mapMain.getG().getTileWidth() + this.mapMain.getG().getDim_circondario() / 2; // Pos tile x
        int agentPosY = agentPos[1] / this.mapMain.getG().getTileHeight() + this.mapMain.getG().getDim_circondario() / 2; // Pos tile y

        System.out.println("agentPosX: " + agentPosX);
        System.out.println("agentPosY: " + agentPosY);

        String payload = task.getProperty("Payload");
        String[] action = payload.split("_");
        System.out.println(task.getProperty("Payload"));

        //Per fermare il walking del client (break)
        if (action[0].equals("STOP")) {
            FIXEDDEST.remove(task.getProperty("Scheduler"));
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");
            nmsg.addNext(get_activity_updatepos(action[1]), "UPDATEDPOS_", task.getProperty("Scheduler"));
        }

        //La guardia, appena dopo una cattura, informa il supervisor che è pronto per un nuovo inseguimento
        //Questo è il messaggio che riceve = lo si aggiunge in lista
        if (action[0].equals("ADDGRD")) {

            System.out.println("Messaggio addgrd ricevuto");
            guardList.add(task.getProperty("Scheduler"));
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");

        }

        if (action[0].equals("MOV")) {
            //Richiesta autorizzazione per un movimento

            //Ricavo la posizione della banca o altra destinazione 
            String[] dest = getDestPosByType(action[1], action[2], task.getProperty("Scheduler")); // Determino posizione porta di ingresso o di uscita in base a payload e tipo di agente
            //String[] bankEntry = DESK_POS[0].split(",");

            int x = Integer.parseInt(dest[0]);
            int y = Integer.parseInt(dest[1]);

//            System.out.println(!(dest.equals(getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))) && !b.isOpen()) && !(dest.equals(getDestPosByType("BANKENTRY", "CLN", task.getProperty("Scheduler")))));
//           System.out.println("dest: "+ dest[0]+ " "+ dest[1]);
//            System.out.println("il metodo mi da: "+ getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))[0]+" "+getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))[1]);
//            System.out.println(dest.equals(getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))));
//            System.out.println(b.isOpen());
//            System.out.println((dest.equals(getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))) && !b.isOpen()));
            if (!((dest[0].equals(getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))[0])
                    && dest[1].equals(getDestPosByType("BANKENTRY", "THF", task.getProperty("Scheduler"))[1]))
                    && !b.isOpen())
                    && !((dest[0].equals(getDestPosByType("BANKENTRY", "CLN", task.getProperty("Scheduler"))[0])
                    && dest[1].equals(getDestPosByType("BANKENTRY", "CLN", task.getProperty("Scheduler"))[1]))
                    && !b.isOpen())) {

                if (mapMain.getG().getMappa_calpestabile()[x][y].equals("1")) {

                    System.out.println("Ingresso libero per " + action[2] + ": " + task.getProperty("Scheduler"));

                    printMappaCalpestabile();

                    int[][] cleanedNotWalkableTiles = getCleanedNotWalkableTiles(agentPosX, agentPosY); // Restituisce le tile non calpestabili senza quella dell'agente corrente

                    AStar aStar = new AStar(mapMain.getG().getMappa_calpestabile()[0].length, mapMain.getG().getMappa_calpestabile().length, agentPosY, agentPosX, y, x, cleanedNotWalkableTiles);

                    aStar.display();
                    aStar.process();
                    aStar.displaySolution();

                    List pathList = aStar.getPathList(); //Contiene lista delle posizioni del percorso da effettuare
                    System.out.println("PathList:" + pathList);

                    String[][] str = getNeighMov(agentPosY, agentPosX); //Vicinato agente

                    //Stampo vicinato in termini di come lo vede l'agente
                    for (int i = 0; i < this.mapMain.getG().getDim_circondario(); i++) {
                        for (int j = 0; j < this.mapMain.getG().getDim_circondario(); j++) {
                            System.out.print(str[i][j] + " ");
                        }
                        System.out.println("");
                    }

                    //Cerco posizione limitrofa da passare all'agente
                    Object[] posizioniNelCirc = new Object[pathList.size()]; //vettore in cui inserisco le posizioni del pathList che individuo nel circondario
                    int posizioneDaInserire = 0; //dimensione vettore di sopra
                    Object posLimitrofa = null;
                    String xPosLimitrofaAgente = null;
                    String yPosLimitrofaAgente = null;
                    for (int i = pathList.size() - 1; i >= 0; i--) {
                        for (int j = 0; j < str.length; j++) {
                            for (int k = 0; k < str[j].length; k++) {
                                if (pathList.get(i).equals(str[j][k])) {
                                    posizioniNelCirc[posizioneDaInserire] = pathList.get(i);
                                    posizioneDaInserire++;
                                    if (pathList.size() - i == posizioneDaInserire) { //controllo che tutti i punti del path, precedenti a quello che sto per settare come posizione limitrofa, cadano all'interno del circondario dell'agente
                                        posLimitrofa = pathList.get(i);
                                        xPosLimitrofaAgente = Integer.toString(j);
                                        yPosLimitrofaAgente = Integer.toString(k);
                                        break;
                                    }
                                }
                            }
                        }

                    }
                    System.out.println("Posizione limitrofa: " + yPosLimitrofaAgente + "," + xPosLimitrofaAgente);

                    // ---> MANDO MESSAGGIO DI APPROVAZIONE
                    String payloadToSend = "APPROVEDMOV_";
                    payloadToSend += yPosLimitrofaAgente;
                    payloadToSend += ",";
                    payloadToSend += xPosLimitrofaAgente;

                    if (action[2].equals("THF") || action[2].equals("CLN") || action[2].equals("GRV")) {
                        if (b.isOpen()) {
                            payloadToSend += "_true";
                        } else {
                            payloadToSend += "_false";
                        }
                    }
                    if (xPosLimitrofaAgente != null && yPosLimitrofaAgente != null) {
                        System.out.println(payloadToSend);
                        nmsg.setActivity(task.getProperty("Activity"));
                        nmsg.setSerial(task.getProperty("Serial"));
                        nmsg.setOutcome("COMPLETED");
                        nmsg.addNext(get_activity_service(action[2]), payloadToSend, task.getProperty("Scheduler"));
                    } else {
                        //La posizione che si vuole raggiungere è irraggiungibile ----> RIFIUTO RICHIESTA
                        System.out.println("Destinazione non disponibile: irraggiungibile");
                        nmsg.setActivity(task.getProperty("Activity"));
                        nmsg.setSerial(task.getProperty("Serial"));
                        nmsg.setOutcome("REFUSED");
                        nmsg.addNext(get_activity_updatepos(action[2]), "UPDATEDPOS_", task.getProperty("Scheduler"));
                    }

                } else {

                    //La posizione che si vuole raggiungere è occupata --> RIFIUTO RICHIESTA
                    System.out.println("Destinazione non disponibile: casella occupata");
                    nmsg.setActivity(task.getProperty("Activity"));
                    nmsg.setSerial(task.getProperty("Serial"));
                    nmsg.setOutcome("REFUSED");
                    nmsg.addNext(get_activity_updatepos(action[2]), "UPDATEDPOS_", task.getProperty("Scheduler"));
                }
            } else {
                //se la banca è chiusa --> RIFIUTO RICHIESTA
                System.out.println("Destinazione non disponibile: banca chiusa");
                nmsg.setActivity(task.getProperty("Activity"));
                nmsg.setSerial(task.getProperty("Serial"));
                nmsg.setOutcome("REFUSED");
                nmsg.addNext(get_activity_updatepos(action[2]), "UPDATEDPOS_", task.getProperty("Scheduler"));
            }

        } else if (action[0].equals("UPDATEPOS")) {

            // AGGIORNO LA POSIZIONE DELL'AGENTE CON QUELLA RICEVUTA DA LUI
            System.out.println("Supervisor UPDATEPOS");

            String[][] str = getNeighMov(agentPosY, agentPosX);  //Vicinato agente

            //preparo variabili per invio posizione
            String[] coord = action[1].split(",");

            int y = Integer.parseInt(coord[0]);
            int x = Integer.parseInt(coord[1]);

            System.out.println("Agente vuole andare in x: " + x + ", y: " + y);

            String dest = str[y][x];

            System.out.println("Che corrisponde a : " + dest);

            //Stampo vicinato in termini di come lo vede l'agente
            for (int i = 0; i < this.mapMain.getG().getDim_circondario(); i++) {
                for (int j = 0; j < this.mapMain.getG().getDim_circondario(); j++) {
                    System.out.print(str[i][j] + " ");
                }
                System.out.println("");
            }

            String[] pos = dest.split(",");

            int y1 = Integer.parseInt(pos[0]);
            int x1 = Integer.parseInt(pos[1]);

            //non eseguo updateAgentPos se l'agente chiede di rimanere dove si trova
            System.out.println("confronto con: " + Integer.parseInt(str[y][x].split(",")[1]) + " " + Integer.parseInt(str[y][x].split(",")[0]));
            if (!(agentPosX == Integer.parseInt(str[y][x].split(",")[1]) && agentPosY == Integer.parseInt(str[y][x].split(",")[0]))) {
                updateAgentPos(task.getProperty("Scheduler")); // la vecchia posizione dell'agente verrà sostituita da un 1 o un 2 nella mappa calpestabile
                //se l'agente transita in una casella di spawn, la setto nel vettore di spawn_occupati a true
                if (mapMain.getG().getMappa_calpestabile()[Integer.parseInt(str[y][x].split(",")[1]) - mapMain.getG().getDim_circondario() / 2][Integer.parseInt(str[y][x].split(",")[0]) - mapMain.getG().getDim_circondario() / 2].equals("2")) {
                    for (int i = 0; i < mapMain.getG().getCaselle_spawn().size(); i++) {
//                        System.out.println(mapMain.getG().getCaselle_spawn().get(i)[0] + " " + Integer.parseInt(str[y][x].split(",")[0]) + " " + mapMain.getG().getCaselle_spawn().get(i)[1] + " " + Integer.parseInt(str[y][x].split(",")[1]));
                        if (mapMain.getG().getCaselle_spawn().get(i)[0] == Integer.parseInt(str[y][x].split(",")[0]) && mapMain.getG().getCaselle_spawn().get(i)[1] == Integer.parseInt(str[y][x].split(",")[1])) {
                            mapMain.getGlass().setSpawn_occupati(i, Boolean.TRUE);
                        }
                    }
                }

                //GESTIONE COLLISIONI
                int[] dest_coord = new int[2]; //coordinate della casella di destinazione richiesta (per gestione collisioni)
                dest_coord[0] = x1;
                dest_coord[1] = y1;

                boolean req_coord = false; //booleano per cercare in requested_coord
                for (int[] coordinata : requested_coord.values()) {
//                    System.out.println("coordinata: " + coordinata[0] + " " + coordinata[1]);
                    if (coordinata[0] == dest_coord[0] && coordinata[1] == dest_coord[1]) {
                        req_coord = true;
                    }
                }
                //se req_coord==true, la posizione è già stata richiesta da un altro agente, per cui non aggiorno la posizione (invio completed per sbloccare il thread di vicinato dell'agente)
                if (req_coord) {
                    System.out.println("Posizione già richiesta da un altro agente");
                    nmsg.addNext(get_activity_updatepos(action[2]), "UPDATEDPOS_", task.getProperty("Scheduler"));
                    nmsg.setActivity(task.getProperty("Activity"));
                    nmsg.setSerial(task.getProperty("Serial"));
                    nmsg.setOutcome("COMPLETED");

                } //se req_coord==false, la posizione non è stata richiesta da altri, la aggiungo alle richieste e processo lo spostamento
                else {
                    if (requested_coord.containsKey(task.getProperty("Scheduler"))) {
                        requested_coord.replace(task.getProperty("Scheduler"), dest_coord);
                    } else {
                        requested_coord.put(task.getProperty("Scheduler"), dest_coord); //aggiungo la casella di destinazione richiesta alla lista delle caselle richieste
                    }

                    System.out.println("con coordinate x1: " + x1 + ", y1:  " + y1);

                    //Effettuo conversione in pixel per salvarlo correttamente in hashmap
                    int[] convertedDest = {(x1 - this.mapMain.getG().getDim_circondario() / 2) * 24, (y1 - this.mapMain.getG().getDim_circondario() / 2) * 24};

                    System.out.println("Destinazione convertita: " + convertedDest[0] + ", " + convertedDest[1]);

                    int[] posintermedia = {mapMain.getGlass().getPosizione(task.getProperty("Scheduler"))[0], mapMain.getGlass().getPosizione(task.getProperty("Scheduler"))[1]};
                    System.out.println("posintermedia " + posintermedia[0] + ", " + posintermedia[1]);
                    System.out.println("convertedDest " + convertedDest[0] + ", " + convertedDest[1]);

                    //Setto la posizione nel glass (fa anche repaint del mapMain) con smooth
                    while (posintermedia[0] != convertedDest[0] || posintermedia[1] != convertedDest[1]) {
                        if (y < this.mapMain.getG().getDim_circondario() / 2) {

                            if (x < this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[0]--;
                                posintermedia[1]--;
//                                System.out.println("posintermedia " + posintermedia[0] + ", " + posintermedia[1]);
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            } else if (x > this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[0]++;
                                posintermedia[1]--;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            } else if (x == this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[1]--;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            }

                        } else if (y > this.mapMain.getG().getDim_circondario() / 2) {

                            if (x < this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[0]--;
                                posintermedia[1]++;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            } else if (x > this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[0]++;
                                posintermedia[1]++;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            } else if (x == this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[1]++;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            }

                        } else if (y == this.mapMain.getG().getDim_circondario() / 2) {

                            if (x < this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[0]--;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            } else if (x > this.mapMain.getG().getDim_circondario() / 2) {
                                posintermedia[0]++;
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            } else if (x == this.mapMain.getG().getDim_circondario() / 2) {
                                mapMain.getGlass().setPosizione(task.getProperty("Scheduler"), posintermedia);
                            }

                        }

                        try {
                            if (action[2].equals("GRD")) {
                                Robbery r = getRobberyByGuard(task.getProperty("Scheduler"));
                                if (r != null && r.isAlarm()) {
                                    Thread.currentThread().sleep(10);
                                } else {
                                    Thread.currentThread().sleep(20);
                                }
                            } else {
                                Thread.currentThread().sleep(20);
                            }
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }

                    //La nuova posizione dell'agente non sarà più calpestabile --> Aggiorno mappa calpestabile con il type CLN,EMP ...
                    if (!mapMain.getG().getMappa_calpestabile()[x1][y1].equals("GRV")) {
                        mapMain.getG().setMappa_calpestabile(action[2], x1, y1);
                    }
                    //Fa sapere all'agente se si tratta dell'ultimo movimento = posizione raggiunta
                    String last_mov = "";

                    if (action.length == 4) {
                        if (action[3].equals("LAST")) {
                            last_mov = action[3] + "_" + doWhereAmI(x1, y1);
                            System.out.println(last_mov);
                            FIXEDDEST.remove(task.getProperty("Scheduler"));
                        } else {
                            last_mov = action[3];
                        }

                    } else {
                        last_mov = "";

                    }

                    //printMappaCalpestabile();
                    // Avviso il cliente che l'aggiornamento è avvenuto correttamente
                    nmsg.addNext(get_activity_updatepos(action[2]), "UPDATEDPOS_" + last_mov, task.getProperty("Scheduler"));
                    nmsg.setActivity(task.getProperty("Activity"));
                    nmsg.setSerial(task.getProperty("Serial"));
                    nmsg.setOutcome("COMPLETED");

                    //Una volta che l'impiegato è operativo e si trova al desk, salvo il suo nick e la posizione del suo desk in empDeskList (dentro activeAgents)
                    if (last_mov.equals("LAST_DESKEMP") && !activeAgents.getEmpDeskList().containsKey(task.getProperty("Scheduler"))) {
                        activeAgents.addDesk(task.getProperty("Scheduler"), x1 + "," + y1);
                        //metodo che setta la banca a open quando l'impiegato raggiunge l'ingresso della banca

                    }
                    if (last_mov.equals("LAST_BANKENTRY") && action[2].equals("EMP")) {
                        if (!b.isOpen()) {
                            setBankOpen(true);
                        }
                    }
                    if (last_mov.equals("LAST_REACHGOLD") && action[2].equals("THF")) {

                        // Il ladro ruba
                        stealedMoney = b.stealMoney();
                        // Nuova rapina
                        robberies.add(new Robbery("", task.getProperty("Scheduler"), new Date(), stealedMoney));

                        //Se i conti si azzerano GAME OVER
                        if (b.getTot() == 0) {
                            endGame();
                            paintComponent();
                        }

                        //Invio msg al ladro per informarlo della somma rubata 
                        NotifyMessage nmsg2 = new NotifyMessage();
                        nmsg2.addNext(THF_3, "ROBTHEBANK_" + stealedMoney, task.getProperty("Scheduler"));
                        conn.send(nmsg2);

                        //Invio msg al cliente per informarlo della somma rubata
                        NotifyMessage nmsg3 = new NotifyMessage();
                        int accountPos = 0;
                        for (Account a : b.getAccounts().values()) {
                            if (a.getSaldo() > 0) {
                                accountPos++;
                            }
                        }
                        for (Account a : b.getAccounts().values()) {
                            nmsg3.addNext(CLN_3, "ROBBERYUPDATE_" + a.getSaldo(), a.getIdAgent());
                            conn.send(nmsg3);
                        }
                    }
                    //Se c'è una rapina vengono tracciate tutte le posizioni dei ladri
                    if (last_mov.equals("NOTLAST") && action[2].equals("THF") && isRobbery()) {
                        System.out.println("Aggiornato ladro");
                        thiefList.put(task.getProperty("Scheduler"), new int[]{agentPosX, agentPosY});

                    }
                    //Il ladro vince
                    if (last_mov.equals("LAST_BUSH") && action[2].equals("THF")) {
                        Robbery r = getRobberyByThief(task.getProperty("Scheduler"));
                        System.out.println("Allarme rientrato");
                        r.setCompleted(true);
                        r.setAlarm(false);
                        if (!isRobbery() && generalAlarm) {
                            generalAlarm = false;

                        }

                        //La guardia è nuovamente disponibile per una nuova cattura
                        NotifyMessage nmsg2 = new NotifyMessage();
                        if (r.getGuard() != null || !r.getGuard().isEmpty()) {
                            nmsg2.addNext(GRD_3, "ALARM_STOP", r.getGuard());
                        }
                        conn.send(nmsg2);
                        checkStatus(nmsg2, true);
                        guardList.add(r.getGuard());

                        //Termino il tracciamento delle posizioni del ladro
                        if (thiefList.containsKey(task.getProperty("Scheduler"))) {
                            thiefList.remove(task.getProperty("Scheduler"));
                            System.out.println("Rimozione da thiefList di: " + task.getProperty("Scheduler"));
                        }

                    }
                }
            } else {
                System.out.println("Posizione già raggiunta");
                nmsg.addNext(get_activity_updatepos(action[2]), "UPDATEDPOS_", task.getProperty("Scheduler"));
                nmsg.setActivity(task.getProperty("Activity"));
                nmsg.setSerial(task.getProperty("Serial"));
                nmsg.setOutcome("COMPLETED");
            }
        }

        //COMPLETO L'ATTIVITA' MOV_BANKENTRY (SUP3) inviando il messaggio costruito negli if precedenti
        conn.send(nmsg);
        showStatus(nmsg, true);
        System.out.println("Chiusura Richiesta movimento");

    }

    //Metodo che restituisce dove ci si trova (per agente) in modo da settare i flag
    private String doWhereAmI(int x1, int y1) {
        String s = "";

        if (BANK_POS_ENTRY[0].split(",")[0].equals(Integer.toString(x1)) && BANK_POS_ENTRY[0].split(",")[1].equals(Integer.toString(y1))
                || BANK_POS_ENTRY_RES[0].split(",")[0].equals(Integer.toString(x1)) && BANK_POS_ENTRY_RES[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Ingresso banca");
            s = "BANKENTRY";

        } else if (BANK_POS_EXIT[0].split(",")[0].equals(Integer.toString(x1)) && BANK_POS_EXIT[0].split(",")[1].equals(Integer.toString(y1))
                || BANK_POS_EXIT_RES[0].split(",")[0].equals(Integer.toString(x1)) && BANK_POS_EXIT_RES[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Uscita banca");
            s = "BANKEXIT";

        } else if (SALA_SLOT_POS_ENTRY[0].split(",")[0].equals(Integer.toString(x1)) && SALA_SLOT_POS_ENTRY[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Ingresso slot");
            s = "SLOTENTRY";

        } else if (SALA_SLOT_POS_EXIT[0].split(",")[0].equals(Integer.toString(x1)) && SALA_SLOT_POS_EXIT[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Uscita slot");
            s = "SLOTEXIT";

        } else if (CAGE_POS_ENTRY[0].split(",")[0].equals(Integer.toString(x1)) && CAGE_POS_ENTRY[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Ingresso gabbia");
            s = "CAGEENTRY";

        } else if (CAGE_POS_EXIT[0].split(",")[0].equals(Integer.toString(x1)) && CAGE_POS_EXIT[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Uscita gabbia");
            s = "CAGEEXIT";

        } else if (DESK_CAGE_POS[0].split(",")[0].equals(Integer.toString(x1)) && DESK_CAGE_POS[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Cage DESK 0");
            s = "CAGEDESK";

        } else if (DESK_CAGE_POS[1].split(",")[0].equals(Integer.toString(x1)) && DESK_CAGE_POS[1].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Cage DESK 1");

            s = "CAGEDESK";

        } else if (DESK_CAGE_POS[2].split(",")[0].equals(Integer.toString(x1)) && DESK_CAGE_POS[2].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Cage DESK 2");

            s = "CAGEDESK";

        } else if (DESK_CAGE_POS[3].split(",")[0].equals(Integer.toString(x1)) && DESK_CAGE_POS[3].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Cage DESK 3");

            s = "CAGEDESK";

        } else if (RONDA_POS[0].split(",")[0].equals(Integer.toString(x1)) && RONDA_POS[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("RONDA 0");
            s = "RONDA";

        } else if (RONDA_POS[1].split(",")[0].equals(Integer.toString(x1)) && RONDA_POS[1].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("RONDA 1");
            s = "RONDA";

        } else if (RONDA_POS[2].split(",")[0].equals(Integer.toString(x1)) && RONDA_POS[2].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("RONDA 2");
            s = "RONDA";

        } else if (BUSH_POS[0].split(",")[0].equals(Integer.toString(x1)) && BUSH_POS[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("BUSH 0");
            s = "BUSH";

        } else if (BUSH_POS[1].split(",")[0].equals(Integer.toString(x1)) && BUSH_POS[1].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("BUSH 1");
            s = "BUSH";

        } else if (BUSH_POS[2].split(",")[0].equals(Integer.toString(x1)) && BUSH_POS[2].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("BUSH 2");
            s = "BUSH";

        } else if (BUSH_POS[3].split(",")[0].equals(Integer.toString(x1)) && BUSH_POS[3].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("BUSH 3");
            s = "BUSH";

        } else if (CAVEAU_POS_ENTRY[0].split(",")[0].equals(Integer.toString(x1)) && CAVEAU_POS_ENTRY[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Entrata caveau");
            s = "CAVEAUENTRY";

        } else if (CAVEAU_POS_EXIT[0].split(",")[0].equals(Integer.toString(x1)) && CAVEAU_POS_EXIT[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Uscita caveau");
            s = "CAVEAUEXIT";

        } else if (TICKET_POS[0].split(",")[0].equals(Integer.toString(x1)) && TICKET_POS[0].split(",")[1].equals(Integer.toString(y1))) {

            System.out.println("Preso biglietto");
            s = "HAVETKT";

        } else if (BANCOMAT_POS[0].split(",")[0].equals(Integer.toString(x1)) && BANCOMAT_POS[0].split(",")[1].equals(Integer.toString(y1))) {
            System.out.println("Arrivato al bancomat");
            s = "BANCOMAT";

        } else if (BANCOMAT_POS[1].split(",")[0].equals(Integer.toString(x1)) && BANCOMAT_POS[1].split(",")[1].equals(Integer.toString(y1))) {
            System.out.println("Arrivato al bancomat");
            s = "BANCOMAT";
        } else {

            for (int i = 0; i < GRASS_POS.length; i++) {

//                System.out.println(GRASS_POS[i] + " è uguale" + x1 + "," + y1);
                if (GRASS_POS[i].equals(x1 + "," + y1)) {

                    s = "WALKING";
                    break;
                }

            }

            for (int i = 0; i < POORGRASS_POS.length; i++) {

//                System.out.println(POORGRASS_POS[i] + " è uguale" + x1 + "," + y1);
                if (POORGRASS_POS[i].equals(x1 + "," + y1)) {

                    s = "POORWALKING";
                    break;
                }

            }

            for (int i = 0; i < BANKPAVEMENT_POS.length; i++) {

//                System.out.println(BANKPAVEMENT_POS[i] + " è uguale" + x1 + "," + y1);
                if (BANKPAVEMENT_POS[i].equals(x1 + "," + y1)) {

                    s = "WALKING";
                    break;
                }

            }

            //Controllo se l'agente si trova al desk
            for (int i = 0; i < 5; i++) {
                String[] pos = DESK_POS[i].split(",");
                //System.out.println("FOR DESK");
                int newPos = Integer.parseInt(pos[1]) + 2;
//                System.out.println(newPos + " è uguale" + x1);
//                System.out.println(Integer.parseInt(pos[0]) + " è uguale" + y1);
                if (newPos == y1 && Integer.parseInt(pos[0]) == x1) {
                    s = "DESK"; // Posizione davanti a employer (per cliente)
                    break;
                } else if (Integer.parseInt(pos[1]) == y1 && Integer.parseInt(pos[0]) == x1) {
                    s = "DESKEMP"; //Posizione al desk
                    break;
                }
            }

            //Controllo se l'agente si trova alla slot
            for (int i = 0; i < SLOT_POS.length; i++) {
                String[] pos = SLOT_POS[i].split(",");
//                System.out.println("FOR SLOT");
                if (Integer.parseInt(pos[1]) == y1 && Integer.parseInt(pos[0]) == x1) {
                    s = "SLOT";
                    break;
                }
            }

            for (int i = 0; i < GOLD_POS.length; i++) {

//                System.out.println(GOLD_POS[i] + " è uguale" + x1 + "," + y1);
                System.out.println("Arrivato ai lingotti");

                if (GOLD_POS[i].equals(x1 + "," + y1)) {
                    s = "REACHGOLD";
                    break;
                }

            }

            for (int i = 0; i < DESK_CAGE_POS.length; i++) {

//                System.out.println(DESK_CAGE_POS[i] + " è uguale" + x1 + "," + y1);
                System.out.println("Cage DESK" + i);

                if (DESK_CAGE_POS[i].equals(x1 + "," + y1)) {
                    s = "CAGEDESK";
                    break;
                }

            }

            for (int i = 0; i < RONDA_POS.length; i++) {

//                System.out.println(RONDA_POS[i] + " è uguale" + x1 + "," + y1);
                System.out.println("RONDA");

                if (RONDA_POS[i].equals(x1 + "," + y1)) {
                    s = "RONDA";
                    break;
                }

            }

        }

        return s;
    }

    /**
     * Metodo che restituisce il circondario dell'agente per valutare
     * successivamente la posizione limitrofa (doMove)
     */
    private String[][] getNeighMov(int agentPosY, int agentPosX) {

        String str[][] = new String[mapMain.getG().getDim_circondario()][mapMain.getG().getDim_circondario()];

        for (int i = 0; i < mapMain.getG().getDim_circondario(); i++) {
            for (int j = 0; j < mapMain.getG().getDim_circondario(); j++) {
                str[i][j] = (agentPosY - mapMain.getG().getDim_circondario() / 2 + i) + "," + (agentPosX - mapMain.getG().getDim_circondario() / 2 + j);
            }
        }
        return str;
    }

    /**
     * Semplice print della mappa calpestabile
     */
    private void printMappaCalpestabile() {
        for (int i = 0; i < mapMain.getG().getMappa_calpestabile()[i].length; i++) {
            for (int j = 0; j < mapMain.getG().getMappa_calpestabile().length; j++) {

                System.out.print(mapMain.getG().getMappa_calpestabile()[j][i] + " ");

            }
            System.out.println("");
        }
    }

    /**
     * Metodo che restituisce l'a destinazione a seconda del payload ricevuto e
     * del tipo di agente
     *
     * @param entryType Stringa che arriva dal payload ricevuto
     * @param type Tipo di agente CLN, EMP, GRD, THF
     * @param scheduler agente che ha fatto la richiesta di movimento
     */
    private String[] getDestPosByType(String entryType, String type, String scheduler) {

        String[] dest = null;

        switch (type) {
            case "CLN":
                if (entryType.equals("BANKENTRY")) {

                    dest = BANK_POS_ENTRY[0].split(",");

                } else if (entryType.equals("BANKEXIT")) {

                    dest = BANK_POS_EXIT[0].split(",");

                } else if (entryType.startsWith("DESK")) {

                    String nick = entryType.substring(4, entryType.length());
                    String desk = activeAgents.getDesk(nick);
                    //System.out.println(desk);
                    //Agente chiede di raggiungere il desk dove si trova l'impiegato con un certo
                    String[] coord = desk.split(",");
                    int newPos = Integer.parseInt(coord[1]) + 2;
                    dest = new String[]{coord[0], Integer.toString(newPos)};
                    //System.out.println("desk pos for client" + dest[0] + "," + dest[1]);

                } else if (entryType.equals("SLOTENTRY")) {

                    dest = SALA_SLOT_POS_ENTRY[0].split(",");

                } else if (entryType.equals("SLOTEXIT")) {

                    dest = SALA_SLOT_POS_EXIT[0].split(",");

                } else if (entryType.equals("WALKING")) {
                    dest = findUniqueDest(scheduler, GRASS_POS);

                } else if (entryType.equals("POORWALKING")) {
                    dest = findUniqueDest(scheduler, POORGRASS_POS);

                } else if (entryType.equals("HAVETKT")) {
                    System.out.println("cerco casella di ticket!");
                    dest = findUniqueDest(scheduler, TICKET_POS);
                } else if (entryType.startsWith("SLOT")) {
                    dest = findUniqueDest(scheduler, SLOT_POS);
                }
                break;
            case "GRV": //caso GRV senza break =THIEF (in OR) 
            case "THF":
                if (entryType.equals("BANKENTRY")) {

                    dest = BANK_POS_ENTRY[0].split(",");

                } else if (entryType.equals("BANKEXIT")) {
                    dest = BANK_POS_EXIT[0].split(",");

                } else if (entryType.equals("CAVEAUENTRY")) {
                    dest = CAVEAU_POS_ENTRY[0].split(",");

                } else if (entryType.equals("REACHGOLD")) {

                    dest = findUniqueDest(scheduler, GOLD_POS);

                } else if (entryType.equals("WALKING")) {
                    dest = findUniqueDest(scheduler, GRASS_POS);
                } else if (entryType.equals("BUSH")) {
                    dest = findUniqueDest(scheduler, BUSH_POS);
                }
                break;
            case "GRD":
                if (entryType.equals("CAGEENTRY")) {
                    dest = CAGE_POS_ENTRY[0].split(",");

                } else if (entryType.equals("CAGEEXIT")) {
                    dest = CAGE_POS_EXIT[0].split(",");

                } else if (entryType.equals("CAGEDESK")) {
                    dest = findUniqueDest(scheduler, DESK_CAGE_POS);

                } else if (entryType.equals("RONDA")) {
                    dest = findUniqueDest(scheduler, RONDA_POS);

                } else if (entryType.startsWith("THIEF")) {
                    String nick = entryType.substring(5, entryType.length());
                    System.out.println("Nick ladro da acchiappare" + nick);
                    //requested_coord.remove(nick); //Stessa posizione richiesta prima dal ladro, quindi la rimuovo
//                    Robbery r = getRobberyByGuard(scheduler);
                    if (thiefList.containsKey(nick)) {
                        System.out.println("Get 0: " + thiefList.get(nick)[0]);
                        System.out.println("Get 1: " + thiefList.get(nick)[1]);
                        dest = new String[]{Integer.toString(thiefList.get(nick)[0]), Integer.toString(thiefList.get(nick)[1])};
//                        chaseList.put(nick, scheduler);
                    } else {
                        dest = "0,0".split(",");
                    }
                } else if (entryType.equals("BANCOMAT")) {

                    System.out.println("BANCOMAT");
                    dest = findUniqueDest(scheduler, BANCOMAT_POS);
                }
                break;
            case "EMP":
                if (entryType.equals("BANKENTRY")) {

                    dest = BANK_POS_ENTRY_RES[0].split(",");

                } else if (entryType.equals("BANKEXIT")) {

                    dest = BANK_POS_EXIT_RES[0].split(",");

                } else if (entryType.equals("CAVEAUENTRY")) {

                    dest = CAVEAU_POS_ENTRY[0].split(",");

                } else if (entryType.equals("CAVEAUEXIT")) {

                    dest = CAVEAU_POS_EXIT[0].split(",");

                } else if (entryType.equals("INSIDECAVEAU")) {

                    dest = findUniqueDest(scheduler, GOLD_POS);

                } else if (entryType.equals("WALKING")) {

                    dest = findUniqueDest(scheduler, BANKPAVEMENT_POS);

                } else if (entryType.equals("DESK")) {

                    if (activeAgents.getEmpDeskList().containsKey(scheduler)) {
                        //Se impiegato era già operativo vuole tornare al suo desk
                        dest = activeAgents.getDesk(scheduler).split(",");
                    } else {
                        //Altrimenti assegno un desk libero
                        dest = getFreeDesk();
                    }

                }
                break;
        }

        return dest;
    }

    /*

    *Metodo che restituisce un desk libero fra i 5 (usato nel metodo getDestPosByType
     */
    private String[] getFreeDesk() {
        String[] deskPos = null;
        boolean found = false;

        for (String s : DESK_POS) {
            deskPos = s.split(",");

            int x = Integer.parseInt(deskPos[0]);
            int y = Integer.parseInt(deskPos[1]);

            if (mapMain.getG().getMappa_calpestabile()[x][y].equals("1") && !activeAgents.getEmpDeskList().containsValue(x + "," + y)) {
                found = true;
                return deskPos;
            }

        }
        if (!found) {
            //Se non trovo un desk libero invio la posizione 0,0 di modo che
            //quando in doMove verifica che sia libero, restituisca false e invii il messaggio refused
            deskPos = "0,0".split(",");
        }
        return deskPos;

    }

    private String[] getFreePos(String[] pos) {
        String[] FreePos = null;

        Random rand = new Random();
        int index = rand.nextInt(pos.length);
        String s = pos[index];
        FreePos = s.split(",");

        int x = Integer.parseInt(FreePos[0]);
        int y = Integer.parseInt(FreePos[1]);

        System.out.println("NON ANCORA trovata casella liber anel getFreePos!");

        if (mapMain.getG().getMappa_calpestabile()[x][y].equals("1")) {
            System.out.println("trovata casella libera nel getFreePos!");
        } else {
            FreePos = "0,0".split(",");
        }

        return FreePos;

    }

    /**
     * Metodo che restituisce la matrice delle tile non calpestabili al netto
     * della posizione dell'agente passato in ingresso
     *
     * @param agentPosX coordinata x della posizione dell'agente
     * @param agentPosY coordinata y della posizione dell'agente
     */
    private int[][] getCleanedNotWalkableTiles(int agentPosX, int agentPosY) {

        System.out.println("Devo rimuovere: " + agentPosX + " Y " + agentPosY);

        int[][] cleanedNotWalkableTiles = new int[mapMain.getG().getNotWalkableTiles().length - 1][2]; //-1 per togliere casella del cliente
        int[][] notWalk = mapMain.getG().getNotWalkableTiles();
        System.out.println("length" + notWalk.length);
        int j = 0;
        for (int i = 0; i < notWalk.length; i++) {

            //System.out.println("notWal " + notWalk[i][0] + "," + notWalk[i][1]);
            if (notWalk[i][1] == agentPosX && notWalk[i][0] == agentPosY) {
                //System.out.println("ECCOLO: " + i );
                continue; // skippo iterata successiva per non aggiungere agentePos
            }
            cleanedNotWalkableTiles[j][0] = notWalk[i][0];
            cleanedNotWalkableTiles[j][1] = notWalk[i][1];
            //System.out.println("cleanedNotWal " + cleanedNotWalkableTiles[j][0] + "," + cleanedNotWalkableTiles[j][1]);

            if (j == notWalk.length - 2) {
                System.out.println("Not walkable già cleaned");
                break;
            }
            j++;

        }
        //System.out.println("J final" + j);

        return cleanedNotWalkableTiles;
    }

    /**
     * Metodo per gestire le presenze degli agenti creati nella mappa
     *
     * @param task identifica il messaggio ottenuto dall'engage ( il payload mi
     * dirà il tipo di agente, lo scheduler l'username)
     */
    private void doPresence(XMLInterface task) {

        int[] posizione;

        HashMap<String, int[]> pos = mapMain.getGlass().getPosizioni();

        //Controllo se l'agente è già presente nella mappa
        if (pos.containsKey(task.getProperty("Scheduler"))) {

            //Se si ottengo la sua posizione
            posizione = pos.get(task.getProperty("Scheduler"));

        } else {

            //altrimenti aggiungo l'agente al panel e ottengo in risposta la sua posizione
            posizione = this.mapMain.getGlass().addAgent(task.getProperty("Scheduler"), task.getProperty("Payload"));
            if (task.getProperty("Payload").equals("GRD")) {
                guardList.add(task.getProperty("Scheduler"));
                for (String g : guardList) {
                    System.out.println("GuardList: " + g);
                }
            }
        }

        //aggiorno la mappa calpestabile inserendo l'agente appena creato nella sua posizione (il tutto è traslato di floor(metà circondario) avendo fatto uno zero padding intorno alla mappa)
        this.mapMain.getG().setMappa_calpestabile(task.getProperty("Payload"), posizione[0] / this.mapMain.getG().getTileWidth() + this.mapMain.getG().getDim_circondario() / 2, posizione[1] / this.mapMain.getG().getTileHeight() + this.mapMain.getG().getDim_circondario() / 2);
        if (!task.getProperty("Payload").equals("GRV")) {
            //creo la mappa delle tile e la mappa calpestabile che dovrò restituire all'agente come suo circondario
            String[][] tile_neigh = new String[this.mapMain.getG().getDim_circondario()][this.mapMain.getG().getDim_circondario()];
            String[][] mappa_calpestabile_restituita = new String[this.mapMain.getG().getDim_circondario()][this.mapMain.getG().getDim_circondario()];

            //setto i valori recuperando le tile e la mappa calpestabile del circondario dell'agente   
            for (int i = 0; i < tile_neigh.length; i++) {
                for (int j = 0; j < tile_neigh.length; j++) {
                    tile_neigh[j][i] = this.mapMain.getG().getTileNumber()[posizione[0] / this.mapMain.getG().getTileWidth() + j][posizione[1] / this.mapMain.getG().getTileHeight() + i];
                    mappa_calpestabile_restituita[j][i] = this.mapMain.getG().getMappa_calpestabile()[posizione[0] / this.mapMain.getG().getTileWidth() + j][posizione[1] / this.mapMain.getG().getTileHeight() + i];
                }
            }

            if (task.getProperty("Payload").equals("THF")) {
                //Controllo situazione allarmi
                if (robberies.size() != 0) {
                    for (Robbery r : robberies) {

                        if (r.checkAlarm()) {
                            //Se una rapina è in corso attivo allarme (se già attivato gestito in activateAlarm)
                            activateAlarm(r.getThief());
                        }
                    }
                } else {
                    System.out.println("Nessuna robbery");
                }
            }

            //Se ci sono rapine in corso e ricevo presenza di una guardia verifico se riesce ad acchiapparne una
            if (isRobbery() && task.getProperty("Payload").equals("GRD")) {

                outerloop:
                for (int i = 0; i < mappa_calpestabile_restituita.length; i++) {
                    for (int j = 0; j < mappa_calpestabile_restituita[i].length; j++) {

                        if (mappa_calpestabile_restituita[i][j].equals("THF")) {

                            String[][] str = getNeighMov((posizione[0] - this.mapMain.getG().getDim_circondario() / 2) / 24, (posizione[1] - this.mapMain.getG().getDim_circondario() / 2) / 24);

                            //Stampa del suo circondario in termini di posizioni MapMain
                            for (int k = 0; k < this.mapMain.getG().getDim_circondario(); k++) {
                                for (int z = 0; z < this.mapMain.getG().getDim_circondario(); z++) {
                                    System.out.print(str[k][z] + " ");
                                }
                                System.out.println("");
                            }

                            int[] thiefPos = new int[]{(Integer.parseInt(str[i][j].split(",")[0])), (Integer.parseInt(str[i][j].split(",")[1]))};
                            String guard = task.getProperty("Scheduler");
                            String thiefKey = null;
                            for (String key : this.mapMain.getGlass().getPosizioni().keySet()) {
                                System.out.println("pos da confrontare di 0: " + (this.mapMain.getGlass().getPosizioni().get(key)[0] - this.mapMain.getG().getDim_circondario() / 2) / 24);
                                System.out.println("pos da confrontare di 1: " + (this.mapMain.getGlass().getPosizioni().get(key)[1] - this.mapMain.getG().getDim_circondario() / 2) / 24);
                                System.out.println("pos ladro 0: " + thiefPos[0]);
                                System.out.println("pos ladro 1: " + thiefPos[1]);
                                if (((this.mapMain.getGlass().getPosizioni().get(key)[0] - this.mapMain.getG().getDim_circondario() / 2) / 24) == thiefPos[0] && ((this.mapMain.getGlass().getPosizioni().get(key)[1] - this.mapMain.getG().getDim_circondario() / 2) / 24) == thiefPos[1]) {
                                    thiefKey = key;
                                    System.out.println("Key ladro: " + thiefKey);
                                    break;
                                }
                            }
                            Robbery r = getRobberyByGuard(guard);
                            if (r != null) {
                                System.out.println("Rapina in corso: " + r.isCompleted());
                                System.out.println("Guardia: " + r.getGuard());
                                System.out.println("Ladro: " + r.getThief());
                                System.out.println("Allarme: " + r.isAlarm());
                                if (r.getThief().equals(thiefKey) && r.getGuard().equals(task.getProperty("Scheduler")) && !r.isCompleted()) {
                                    /*
                            X = se il ladro è lì, muore!
                            0 0 0 0 0
                            0 X X X 0
                            0 X G X 0
                            0 X X X 0
                            0 0 0 0 0
                            
                                     */
                                    if (Math.abs(i - this.mapMain.getG().getDim_circondario() / 2) <= 1) {
                                        if (Math.abs(j - this.mapMain.getG().getDim_circondario() / 2) <= 1) {
                                            System.out.println("ho catturato il ladro!");

                                            NotifyMessage nmsg1 = new NotifyMessage();
                                            nmsg1.addNext(THF_3, "PLANFAILED_", thiefKey);  //uccido il ladro
                                            conn.send(nmsg1);
                                            showStatus(nmsg1, true);

                                            r.setCompleted(true);
                                            r.setAlarm(false);

                                            if (!isRobbery() && generalAlarm) {
                                                generalAlarm = false;
                                            }

                                            NotifyMessage nmsg2 = new NotifyMessage();
                                            //Avviso guardia della cattura e dei soldi da restituire
                                            nmsg2.addNext(GRD_3, "ALARM_STOP_CATCHED" + r.getStealedMoney(), guard); //ocho a GRD_3
                                            conn.send(nmsg2);
                                            showStatus(nmsg2, true);

                                            mapMain.getGlass().setImageAgent(thiefKey, alloverdev.Assets.image("Immagini_agenti/cryingThiefPNG.png"));
                                            System.out.println("Print THF" + mapMain.getG().getMappa_calpestabile()[thiefPos[0]][thiefPos[1]]);
//                                        this.mapMain.getG().setMappa_calpestabile("GRV", thiefPos[0] + this.mapMain.getG().getDim_circondario() / 2, thiefPos[1] + this.mapMain.getG().getDim_circondario() / 2);
//                                            mappa_calpestabile_restituita[i][j] = "GRV";
                                        }
                                    }
                                    break outerloop;
                                }

                            } else {
                                System.out.println("Nessuna rapina associata ala guardia");
                            }
                        }
                    }
                }
            }
            //Preparo il messaggio con il circondario da mandare all'agente
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");
            //Il payload conterrà le informazioni sul suo circondario e viene generato attraverso due metodi
            nmsg.addNext(get_activity_neigh(task.getProperty("Payload")), get_Payload_neigh(this.mapMain.getG().getDim_circondario(), tile_neigh, mappa_calpestabile_restituita), task.getProperty("Scheduler"));
            conn.send(nmsg);
            showStatus(nmsg, true);
        } else {
            NotifyMessage nmsg = new NotifyMessage();
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");
            nmsg.addNext(KILLME, "KILL", task.getProperty("Scheduler"));  //uccido il ladro
            conn.send(nmsg);
            showStatus(nmsg, true);
        }
    }

    /*
    *Metodo che restituisce la relativa activity dell'agente  a seconda del tipo passato in ingresso
    *@param type Stringa che rappresenta l'agentType
     */
    private String get_activity_neigh(String type) {
        String activity = null;

        switch (type) {
            case "CLN":
                activity = CLN_2;
                break;
            case "THF":
                activity = THF_2;
                break;
            case "GRD":
                activity = GRD_2;
                break;
            case "EMP":
                activity = EMP_2;
                break;
            case "GRV":
                activity = THF_2;
                break;
        }

        return activity;
    }

    /*
    *Metodo che restituisce la relativa activity dell'agente per updatepos a seconda del tipo passato in ingresso
    *@param type Stringa che rappresenta l'agentType
     */
    private String get_activity_updatepos(String type) {
        String activity = null;

        switch (type) {
            case "CLN":
                activity = CLN_5;
                break;
            case "THF":
                activity = THF_5;
                break;
            case "GRD":
                activity = GRD_5;
                break;
            case "EMP":
                activity = EMP_5;
                break;
            case "GRV":
                activity = THF_5;
                break;
        }

        return activity;
    }

//  Metodo che restituisce una destinazione per passeggiate, ronda ecc. (movimenti randomici generici) differente per ogni agente
    private String[] findUniqueDest(String id, String[] where) {
        String[] uniqueDest;
        boolean found = false;
        for (String s : FIXEDDEST.keySet()) {
            System.out.println("fixeddest dentro: " + FIXEDDEST.get(s)[0] + " " + FIXEDDEST.get(s)[1]);
            if (s.equals(id) && !(FIXEDDEST.get(s)[0].equals("0") && FIXEDDEST.get(s)[1].equals("0"))) {
                found = true;
            }
        }
        if (found) {
            uniqueDest = FIXEDDEST.get(id);
        } else {
            FIXEDDEST.put(id, getFreePos(where));
            uniqueDest = FIXEDDEST.get(id);
        }
        return uniqueDest;
    }

    /*
    *Metodo che restituisce la relativa activity "service" dell'agente  a seconda del tipo passato in ingresso
    *@param type Stringa che rappresenta l'agentType
     */
    private String get_activity_service(String type) {
        String activity = null;

        switch (type) {
            case "CLN":
                activity = CLN_1;
                break;
            case "THF":
                activity = THF_1;
                break;
            case "GRD":
                activity = GRD_1;
                break;
            case "EMP":
                activity = EMP_1;
                break;
            case "GRV":
                activity = THF_1;
                break;
        }

        return activity;
    }

    /*
    * Metodo per la costruzione del payload da inviare all'agente
    * @param dim_circondario dimensione del circondario considerato per agente 
   *  @param tile_neigh tile del circondario
    * @param mappa_calpestabile rappresenta le caselle calpestabili (1) o no (0) o la presenza di un altro agente (CLN, EMP ..)
     */
    private String get_Payload_neigh(int dim_circondario, String[][] tile_neigh, String[][] mappa_calpestabile) {
        String s = Integer.toString(dim_circondario);
        s += ",";
        for (int i = 0; i < dim_circondario; i++) {
            for (int j = 0; j < dim_circondario; j++) {
                s += tile_neigh[i][j];
                s += ",";
            }
        }
        for (int i = 0; i < dim_circondario; i++) {
            for (int j = 0; j < dim_circondario; j++) {
                s += mappa_calpestabile[i][j];
                if ((i == dim_circondario - 1) && (j == dim_circondario - 1)) {

                } else {
                    s += ",";
                }
            }
        }
        //System.out.println(s);
        return s;
    }

    /**
     * Metodo chiamato dalla MapMain qualora si chiuda il Supervisor
     */
    public void endGame() {
        NotifyMessage nmsg = new NotifyMessage();
        nmsg.addNext(KILLME, "KILL", "ALL");
        getConn().send(nmsg);
        showStatus(nmsg, true);
        setRunning(false); // imposto questo flag a false in modo che non ci sia il rischio che qualche processo continui a girare
    }

    //Metodo Shutdown per chiudere definitivamente il gioco qualora in Supervisor non sia più running e non ci siano più agenti
    //Vedi ScannerThread
    private synchronized void shutdown() {
        notifyAll();
        subscribe(true);
        System.exit(0);
    }

    //Metodo che restituisce l'oggetto AgentList con all'interno hashmap degli agenti attivi
    private AgentList getActiveAgents() {
        return activeAgents;
    }

    private void doBankingOperation(XMLInterface task) {
        System.out.println("doBankingOperation");
        String[] s = task.getProperty("Payload").split(",");
        String s0 = s[0]; //tipo di operazione
        if (s0.equals("OP")) {
            String s1 = s[1]; //cifra
            String s2 = s[2]; //wallet
            String s3 = s[3]; //id Agente
            boolean x;
            double cifra = Double.parseDouble(s1);
            double wallet = Double.parseDouble(s2);

            NotifyMessage nmsg = new NotifyMessage();

            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");

            //il deposito può sempre essere fatto
            //viene fatto un controllo sulla cifra da prelevare, se il conto ha abbastanza denaro preleva
            //altrimenti si controlla se la somma tra conto e wallet è sufficiente per giocare alle slot
            //se è sufficiente preleva tutto il denaro sul conto, altrimenti muore           
            if (cifra < 0 && Math.abs(cifra) >= b.getConto(s3).getSaldo()) {
                cifra = b.getConto(s3).getSaldo();
                x = b.operazione(s3, (int) (-cifra));
                System.out.println("Non ho effettuato l'operazione richiesta " + s1 + " denaro disponibile prelevato " + (-cifra));
                nmsg.addNext(EMP_4, "CHECK," + (-cifra) + "," + b.getConto(s3).getSaldo() + "," + s3, task.getProperty("Scheduler"));
            } else {
                x = b.operazione(s3, (int) cifra);
                System.out.println("ho effettuato l'operazione richiesta: " + s1);
                nmsg.addNext(EMP_4, "CHECK," + cifra + "," + b.getConto(s3).getSaldo() + "," + s3, task.getProperty("Scheduler"));
            }

            conn.send(nmsg);
            checkStatus(nmsg, true);
        } else if (s0.equals("GRD")) {
            NotifyMessage nmsg = new NotifyMessage();

            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");
            conn.send(nmsg);
            checkStatus(nmsg, true);
            String s1 = s[1]; //cifra
            double cifra = Double.parseDouble(s1); // soldi da restituire
            System.out.println("Soldi da restituire: " + cifra);
            b.putMoneyBack((int) cifra);

            NotifyMessage nmsg3 = new NotifyMessage();
            for (Account a : b.getAccounts().values()) {
                nmsg3.addNext(CLN_3, "ROBBERYUPDATE_" + a.getSaldo(), a.getIdAgent());
                conn.send(nmsg3);
            }

        }

        if (b.getTot() == 0) {
            endGame();
            paintComponent();
        }

    }

    private void doSlotOperation(XMLInterface task) {

        String[] s = task.getProperty("Payload").split(",");
        String s1 = s[1]; //giocata
//        String s2 = s[2]; //ID slot
        String s2 = s[2]; // wallet

        double cifra = Double.parseDouble(s1);
        double wallet = Double.parseDouble(s2);

//        char a = s2.charAt(4);
//        int z = Character.getNumericValue(a);
        NotifyMessage nmsg = new NotifyMessage();

        if (cifra <= wallet) {

            wallet = slot.operazioneSlot(cifra, wallet);

            //wallet = slot.operazioneSlot(cifra, wallet);
            System.out.println("ho effettuato la giocata richiesta: " + s1);

            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("COMPLETED");

//            slots.get(z - 1).setSlotInUso(false);
        } else {
            nmsg.setActivity(task.getProperty("Activity"));
            nmsg.setSerial(task.getProperty("Serial"));
            nmsg.setOutcome("REFUSED");

//            slots.get(z - 1).setSlotInUso(false);
        }

        nmsg.addNext(CLN_7, "GETGIOCO," + wallet, task.getProperty("Scheduler"));

        conn.send(nmsg);
        checkStatus(nmsg, true);

    }

    private void activateAlarm(String idThief) {
        Robbery r = getRobberyByThief(idThief);
        System.out.println("\nL'allarme è stato attivato e verrà richiamata la guardia!");
        if (isRobbery() && !generalAlarm) {
            generalAlarm = true;
            synchronized (alarmThread.alarmLock) {
                System.out.println("Activate Alarm notify");
                alarmThread.alarmLock.notify();
            }
        }
        if (!guardList.isEmpty() && !r.isCompleted()) {
            System.out.println("GuardList pieno e rapina non completata");
            if (r.getGuard() == null || r.getGuard().isEmpty()) {
                System.out.println("Nessuna guardia assegnata");
                String g = guardList.removeFirst();
                r.setGuard(g);
                NotifyMessage nmsg = new NotifyMessage();
                nmsg.addNext(GRD_3, "ALARM_" + idThief, g);
                conn.send(nmsg);
                checkStatus(nmsg, true);
            }
        } else {
            System.out.println("Nessuna guardia disponibile per la rapina o già assegnata  o rapina completata");
        }
//        NotifyMessage nmsg2 = new NotifyMessage();
//        nmsg2.addNext(ALARM, "ALARM_", "ALL");
//        conn.send(nmsg2);
//        checkStatus(nmsg2, true);

    }

    private boolean isRobbery() {
        boolean robbery = false;
        if (robberies.size() != 0) {
            for (Robbery r : robberies) {
                if (r.isAlarm() && !r.isCompleted()) {
                    robbery = true;
                    break;
                }
            }
        }
        return robbery;
    }

    private Robbery getRobberyByGuard(String idGuard) {
        Robbery robbery = null;
        if (robberies.size() != 0) {
            for (Robbery r : robberies) {
                if (r.getGuard().equals(idGuard) && !r.isCompleted()) {
                    robbery = r;
                    break;
                }
            }
        }
        return robbery;
    }

    private Robbery getRobberyByThief(String idThief) {
        Robbery robbery = null;
        if (!robberies.isEmpty()) {
            for (Robbery r : robberies) {
                if (r.getThief().equals(idThief) && !r.isCompleted()) {
                    robbery = r;
                    break;
                }
            }
        }
        return robbery;
    }

    private class AlarmThread extends Thread {

        boolean light = false;
        Object alarmLock = new Object();

        // ==== ON ====
        Image imgBankOnR = alloverdev.Assets.image("Tiles/132.jpg");
        Image imgBankOnL = alloverdev.Assets.image("Tiles/133.jpg");
        Image imgCaveauOn = alloverdev.Assets.image("Tiles/131.jpg");
        // ==== OFF ====
        Image imgBankOffR = alloverdev.Assets.image("Tiles/2.jpg");
        Image imgBankOffL = alloverdev.Assets.image("Tiles/1.jpg");
        Image imgCaveauOff = alloverdev.Assets.image("Tiles/72.jpg");

        @Override
        public void run() {

            Thread.currentThread().setName("AT");
            synchronized (alarmLock) {
                for (;;) {

                    try {
                        alarmLock.wait();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    while (generalAlarm) {
                        // ==== ON ====
                        if (!light) {
                            light = true;
                            mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMC_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMC_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgCaveauOn);
                            mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMBL_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMBL_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgBankOnL);
                            mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMBR_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMBR_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgBankOnR);
                            mapMain.getG().repaint();
                            try {

                                Thread.currentThread().sleep(500);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        } // ==== OFF ====
                        else {
                            light = false;
                            mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMC_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMC_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgCaveauOff);
                            mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMBL_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMBL_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgBankOffL);
                            mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMBR_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMBR_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgBankOffR);
                            mapMain.getG().repaint();
                            try {

                                Thread.currentThread().sleep(500);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        }

                    }

                    light = false;
                    mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMC_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMC_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgCaveauOff);
                    mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMBL_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMBL_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgBankOffL);
                    mapMain.getG().getMappa().setTile(Integer.parseInt(ALARMBR_POS[0].split(",")[0]) - mapMain.getG().getDim_circondario() / 2, Integer.parseInt(ALARMBR_POS[0].split(",")[1]) - mapMain.getG().getDim_circondario() / 2, imgBankOffR);
                    mapMain.getG().repaint();
                } // for 
            }

        }
    }

    private void paintComponent() {

        JFrame frame = new JFrame();
        JLabel label = new JLabel();

        ImageIcon icon = new ImageIcon(alloverdev.Assets.image("Immagini_menu/GameOver.png"));
        label.setIcon(icon);
        frame.add(label);

        frame.setSize(1200, 800);
        frame.setUndecorated(true);

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((int) (dim.getWidth() / 2 - frame.getWidth() / 2), (int) (dim.getHeight() / 2 - frame.getHeight() / 2));
        frame.setResizable(false);
        frame.setVisible(true);

    }

    //Restituisce se il supervisor è operativo o no
    private boolean isRunning() {
        return running;
    }

    //Metodo che setta l'operatività del supervisor
    //@param running rappresenta il booleano da settare
    private void setRunning(boolean running) {
        this.running = running;
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

    public void setBankOpen(boolean open) {
        this.b.setOpen(open);
    }

}
