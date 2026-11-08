package alloverdev.operation;

import alloverdev.exception.InvalidOperationException;

public class Account {

    private int saldo;
    private String idAgent;

    public Account(String idAgent) {
        this.idAgent = idAgent;
        this.saldo = (int) (Math.floor((Math.random() * 5000) * 100) / 100 + 10000);
    }

    public boolean operazione(int amount) {
        if (amount >= 0) {
            return deposito(amount);

        } else {
            return prelievo(-amount);
        }
    }

    private boolean deposito(int amount) {
        saldo += amount;
        return true;
    }

    private boolean prelievo(int amount) {
        if (amount <= saldo) {
            saldo -= amount;
            return true;
        } else {
            throw new InvalidOperationException("Conto: " + this.getSaldo() + " not allowed operation");
        }
    }

    public double getSaldo() {
        return saldo;
    }

    public String getIdAgent() {
        return idAgent;
    }

}
