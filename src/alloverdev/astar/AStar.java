package alloverdev.astar;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class AStar {

//costo per movimenti verticali, orizzontali, diagonali
    public static final int DIAGONAL_COST = 141;
    public static final int V_H_COST = 100;

// Istanzio l'intera mappa
    private Cell[][] grid;

//Definisco una coda per le celle libere, 
//costruita mettendo ai primi posti le celle con il costo minore
    private PriorityQueue<Cell> openCells;

// set di celle già valutate, true se si false se no
    private boolean closedCells[][];

// !!!! I:RIGA, J:COLONNA !!!!!
// coordinate cella iniziale
    private int startY, startX;

// coordinate cella finale
    private int endY, endX;

    private List pathList;

    public AStar(int width, int height, int sy, int sx, int ey, int ex, int[][] blocks) {

        grid = new Cell[width][height];
        closedCells = new boolean[width][height];

        openCells = new PriorityQueue<Cell>((Cell c1, Cell c2) -> {
            return c1.finalCost < c2.finalCost ? -1 : c1.finalCost > c2.finalCost ? 1 : 0;
        });

        pathList = new ArrayList<String>();
        startCell(sy, sx);
        endCell(ey, ex);

        // inizializzazione della funzione euristica
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                grid[i][j] = new Cell(i, j);
                grid[i][j].heuristicCost = Math.abs(i - endY) + Math.abs(j - endX);
                // Verifico i costi euristici stampandoli
                System.out.print(grid[i][j].heuristicCost + " ");
                grid[i][j].solution = false;
            }
            System.out.print("\n");
        }

        System.out.println();

        grid[startY][startX].finalCost = 0;

        // inserisco i blocchi
        for (int i = 0; i < blocks.length; i++) {
            addBlockOnCell(blocks[i][0], blocks[i][1]);
        }

    }

    public void addBlockOnCell(int y, int x) {
        grid[y][x] = null;
    }

    public void startCell(int i, int j) {
        startY = i;
        startX = j;
    }

    public void endCell(int i, int j) {
        endY = i;
        endX = j;
    }

    public void updateCostIfNeeded(Cell current, Cell t, int cost) {
        if (t == null || closedCells[t.y][t.x]) {
            return;
        }

        int tFinalCost = t.heuristicCost + cost;
        boolean isOpen = openCells.contains(t);

        if (!isOpen || tFinalCost < t.finalCost) {
            t.finalCost = tFinalCost;
            t.parent = current;

            if (!isOpen) {
                openCells.add(t);
            }
        }

    }

    public void process() {

// aggiungo la cella iniziale alla lista open
        openCells.add(grid[startY][startX]);
        Cell current;

        while (true) {
            current = openCells.poll(); //poll ritorna la head della lista, null se vuota

            if (current == null) {
                break;
            }

            closedCells[current.y][current.x] = true;

            if (current.equals(grid[endY][endX])) {
                return;
            }

            Cell t;

            if (current.y - 1 >= 0) {
                t = grid[current.y - 1][current.x];
                updateCostIfNeeded(current, t, current.finalCost + V_H_COST);

                if (current.x - 1 >= 0) {
                    t = grid[current.y - 1][current.x - 1];
                    updateCostIfNeeded(current, t, current.finalCost + DIAGONAL_COST);
                }

                if (current.x + 1 < grid[0].length) {
                    t = grid[current.y - 1][current.x + 1];
                    updateCostIfNeeded(current, t, current.finalCost + DIAGONAL_COST);

                }
            }

            if (current.x - 1 >= 0) {
                t = grid[current.y][current.x - 1];
                updateCostIfNeeded(current, t, current.finalCost + V_H_COST);
            }

            if (current.x + 1 < grid[0].length) {
                t = grid[current.y][current.x + 1];
                updateCostIfNeeded(current, t, current.finalCost + V_H_COST);
            }

            if (current.y + 1 < grid.length) {
                t = grid[current.y + 1][current.x];
                updateCostIfNeeded(current, t, current.finalCost + V_H_COST);

                if (current.x - 1 >= 0) {
                    t = grid[current.y + 1][current.x - 1];
                    updateCostIfNeeded(current, t, current.finalCost + DIAGONAL_COST);
                }

                if (current.x + 1 < grid[0].length) {
                    t = grid[current.y + 1][current.x + 1];
                    updateCostIfNeeded(current, t, current.finalCost + DIAGONAL_COST);
                }
            }
        }
    }

    public void display() {

        System.out.println("Grid :");

        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                if (i == startY && j == startX) {
                    System.out.print("SC "); // Source cell 
                } else if (i == endY && j == endX) {
                    System.out.print("DC "); // Destination cell 
                } else if (grid[i][j] != null) //System.out.printf("%-3d ", 0);
                {
                    System.out.print("0 ");
                } else {
                    System.out.print("BL "); // Block Cell
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    public void displayScores() {
        System.out.println("\nScores for cells :");

        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                if (grid[i][j] != null) //System.out.printf("%-3d ", grid[i][j].finalCost);
                {
                    System.out.print(grid[i][j].finalCost + " ");
                } else {
                    System.out.print("BL ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    public void displaySolution() {

        if (closedCells[endY][endX]) {
            // percorro il path a ritroso
            System.out.println("Path :");
            Cell current = grid[endY][endX];
            System.out.print(current);
            grid[current.y][current.x].solution = true;

            while (current.parent != null) {
                System.out.print("<-" + current.parent);
                grid[current.parent.y][current.parent.x].solution = true;
                //pathList.add(current.y+ ","+ current.x);
                current = current.parent;
            }

            System.out.println("\n");

            for (int i = 0; i < grid.length; i++) {
                for (int j = 0; j < grid[i].length; j++) {
                    if (i == startY && j == startX) {
                        System.out.print("S "); // Source cell
                    } else if (i == endY && j == endX) {
                        System.out.print("D "); // Destination cell
                    } else if (grid[i][j] != null) //System.out.printf("%-3s ", grid[i][j].solution ? "X" : "0");
                    {
                        System.out.print(grid[i][j].solution ? "X " : "0 ");
                    } else {
                        System.out.print("B "); // Block Cell
                    }
                }
                System.out.println();
            }
            System.out.println();
        } else {
            System.out.println("No possible path");
        }
    }

    public Cell[][] getGrid() {
        return grid;
    }

    public List getPathList() {
        if (closedCells[endY][endX]) {
            // percorro il path a ritroso
            Cell c = grid[endY][endX];
            while (c.parent != null) {
                pathList.add(c.y + "," + c.x);
                c = c.parent;
            }
        }
        return pathList;
    }

    public int getStartY() {
        return startY;
    }

    public int getStartX() {
        return startX;
    }

    public int getEndY() {
        return endY;
    }

    public int getEndX() {
        return endX;
    }

    public boolean[][] getClosedCells() {
        return closedCells;
    }

// MAIN DI PROVA
    public static void main(String[] args) {
        AStar aStar = new AStar(5, 5, 3, 2, 0, 0, //In ordine:altezza e larghezza griglia, riga e colonna inizio e arrivo
                new int[][]{
                    {0, 4}, {2, 2}, {3, 1}, {3, 3}, {2, 1}, {2, 3}, {4, 1}//, {3,4} //coordinate blocchi esempio
                }
        );

        aStar.display();
        aStar.process(); // Applicazione algoritmo A* 
        aStar.displayScores(); // Mostro i punteggi sulla griglia 
        aStar.displaySolution(); // Mostro il percorso soluzione
    }
}
