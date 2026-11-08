package alloverdev.agents;

import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentList {

    private Map<String, Date> list; //agenti attivi
    private LinkedList<String> bankQueue; //coda in banca
    private Map<String, String> empDeskList; //corrispondenze impiegato - desk (coordinate posizione)

    public AgentList() {
        this.list = new ConcurrentHashMap<>();
        this.bankQueue = new LinkedList<>();
        this.empDeskList = new ConcurrentHashMap<>();
    }

    /**
     * Metodo che aggiunge un agente nella hashMap per tracciare gli agenti
     * attivi e le date dell'ultima segnalazione di presenza Esso viene
     * richiamato all'interno di doPresence
     *
     * @param nick rappresenta id dell'agente da aggiungere del tipo 'CLXXXXX'
     */
    public void addAgent(String nick) {
        for (String s : list.keySet()) {
            int comparison = nick.compareTo(s);
            //System.out.println(comparison);
            if (comparison > 0) { // nick viene dopo quello in esame (cerca ancora)
                continue;
            } else if (comparison == 0) { // trovato ! (aggiorno solo il timestamp ed esco)
                list.put(nick, new Date());
                return;
            } else { // Non trovato (aggiungi nick e timestamp)
                break;
            }
        }
        list.put(nick, new Date());
    }

    public void removeAgent(String nick) {
        System.out.println("Rimosso agente");
        list.remove(nick);

    }

    public Map<String, Date> getList() {
        return list;
    }

    public void setList(Map<String, Date> list) {
        this.list = list;
    }

    public Date getDate(String nick) {
        return list.get(nick);
    }

    public synchronized boolean addToBankQueue(String nick) {
        //aggiunge un oggetto x in coda alla lista. 
        //Restituisce un valore booleano perché è obbligato da una superclasse
        return bankQueue.add(nick);
    }

    public synchronized String removeFromBankQueue() {
        //rimuove e restituisce la testa della lista. Solleva l’eccezione NoSuchElementException se la lista è vuota.
        return bankQueue.removeFirst();
    }

    public void addDesk(String nick, String desk) {
        empDeskList.put(nick, desk);
    }

    public void removeDesk(String nick) {
        empDeskList.remove(nick);
    }

    public String getDesk(String nick) {
        return empDeskList.get(nick);
    }

    public int getBankQueueSize() {
        return bankQueue.size();
    }

    //Sconsigliato utilizzo
    public LinkedList<String> getBankQueue() {
        return bankQueue;
    }

    public Map<String, String> getEmpDeskList() {
        return empDeskList;
    }

}
