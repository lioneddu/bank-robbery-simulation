package alloverdev.astar;

// definisco l'oggetto cella
public class Cell {
// coordinate

    public int y, x;

// parent cell for path
    public Cell parent;
// costo euristico per la cella corrente
    public int heuristicCost;
// costo finale
    public int finalCost; // G+H 
    public boolean solution;

// G(n) è il costo del percorso a partire dal nodo di inizio fino al nodo n
// e H(n) l'euristica che calcola il costo del percorso più "economico" da n 
// all'obiettivo se la cella fa parte del percorso soluzione.
    public Cell(int y, int x) {
        this.y = y;
        this.x = x;
    }

    @Override

    public String toString() {
        return "[" + y + +x + "]";
    }

}
