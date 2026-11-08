package alloverdev.operation;

public class Slot {

    private double denaro;
    private double giocata;

    public Slot() {
        this.denaro = 0.0;
        this.giocata = 0.0;
    }

    public int estraiNumero() {
        int n;
        n = (int) (Math.random() * 10);
        return n;
    }

    public double operazioneSlot(double g, double w) {
        int x = estraiNumero();
        if (x == 1) {
            System.out.println("E' stato estratto il numero " + x);
            return vincita(g, w);
        } else {
            System.out.println("E' stato estratto il numero " + x);
            return perdita(g, w);
        }
    }

    public double vincita(double giocata, double wallet) {

        int n = estraiNumero();

        switch (n) {
            case 1:
            case 2:
            case 3:
            case 4:
                denaro = giocata * 2;
                break;
            case 5:
            case 6:
            case 7:
            case 8:
                denaro = giocata * 3;
                break;
            case 9:
            case 10:
                denaro = giocata * 4;
                break;

        }

        wallet += denaro;
        System.out.println("abbiamo vinto " + denaro);

        denaro = 0.0;

        return wallet;

    }

    public double perdita(double giocata, double wallet) {

        wallet -= giocata;
        return wallet;
    }

    public double getGiocata() {
        return giocata;
    }

    public void setGiocata(double giocata) {
        this.giocata = giocata;
    }

}
