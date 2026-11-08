package alloverdev.operation;

import alloverdev.exception.InvalidOperationException;
import alloverdev.operation.Account;
import static java.lang.Math.random;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class Bank {

    private Map<String, Account> accounts;
    private int means; // denaro totale della banca
    private boolean open;

    public Bank() {
        accounts = new LinkedHashMap<>();
        this.means = getTot();
        this.open = false;
    }

    public int getTot() {
        int tot = 0;
        for (Account a : accounts.values()) {
            tot += a.getSaldo();
        }
        return tot;
    }

    /*
    * Aggiunge conto in banca
    *@param idAgent username per il quale creare il conto in banca
     */
    public void addAccount(String idAgent) {
        Account c = new Account(idAgent);
        accounts.put(idAgent, c);
        System.out.println("Ho creato il conto! Cliente: " + c.getIdAgent() + " saldo: " + c.getSaldo());
        getTot();
    }

    /*
    * Rimuove conto in banca
    *@param idAgent username per il quale rimuovere il conto in banca
     */
    public void removeAccount(String idAgent) {
        accounts.remove(idAgent);
    }

    /*
    * Effettua operazione in banca
    *@param idAgent username per ottenere il conto
    *@param amount ammontare con il quale operare
     */
    public boolean operazione(String idAgent, int amount) {

        Account c = null;
        try {
            c = getConto(idAgent);
            getTot();
            return c.operazione((int) amount);
        } catch (InvalidOperationException e) {
            System.err.println("Conto di " + idAgent + ": " + e.getMessage());
        }
        return false;
    }

    /*
    * Restituisce conto
    *@param idAgent username per ottenere il conto
     */
    public Account getConto(String idAgent) {
        //Diamo per scontato che esista un conto
        return accounts.get(idAgent);
    }

    /*
    *  Metodo per rubare soldi
    * Viene rubato il saldo minore fra tutti i conti
     */
    public int stealMoney() {

        int stealedMoney;
        int accountPos = 0;
        //Se esistono dei conti
        if (!accounts.isEmpty()) {
            //Cerco il saldo più piccolo tra i conti
            Account minAccount = null;
            int i = 0;
            for (Account a : accounts.values()) {
                if (i == 0) {
                    minAccount = a;                    
                }
                System.out.println("Saldo MinAccount: "+minAccount.getSaldo());
                System.out.println("Saldo aAccount: "+a.getSaldo());
                if (a.getSaldo() > 1) {
                    accountPos++;
                    if (a.getSaldo() < minAccount.getSaldo() || minAccount.getSaldo() <= 0) {
                        minAccount = a;
                    }
                }
                i++;
            }

            //Se il saldo del conto più piccolo è basso (<6000) rubo esattamente il saldo di quel conto
            System.out.println("Il ladro vuole rubare min Account saldo: " + Math.floor((minAccount.getSaldo() * accountPos) * 100) / 100);
            stealedMoney = (int) (minAccount.getSaldo() * accountPos);

        } else {
            //Nessun conto rubo 0
            stealedMoney = 0;
        }
        
        int realStealedMoney=0;

        //Divido equamente tra i conti il quantitativo rubato
        for (Account a : accounts.values()) {
          
            if (a.getSaldo() > 1 ) {
                a.operazione(-(stealedMoney / accountPos));
                realStealedMoney+=stealedMoney / accountPos;
            } else {
                a.operazione(0);
            }
            System.out.println("Soldi rubati DAVVERO: "+Integer.toString(realStealedMoney));
            System.out.println("Saldo conto: " + Math.floor(a.getSaldo() * 100.0) / 100.0);
        }
        return (int) (Math.floor(realStealedMoney * 100) / 100);
    }

    public void putMoneyBack(int money) {
        for (Account a : accounts.values()) {

            a.operazione((int) (+Math.floor((money / accounts.size()) * 100.0) / 100.0)); //Divido equamente tra i conti i soldi restituiti
            System.out.println("Saldo conto: " + a.getSaldo());

        }
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    public Map<String, Account> getAccounts() {
        return accounts;
    }

}
