package alloverdev;

import alloverdev.agents.Agent;
import alloverdev.agents.Client;
import alloverdev.agents.Employee;
import alloverdev.agents.Guard;
import alloverdev.agents.Supervisor;
import alloverdev.agents.Thief;
import alloverdev.broker.AgentTerminated;
import alloverdev.broker.Connection;
import alloverdev.interfaces.AgentsIF;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher of the whole simulation in a single process.
 *
 * <p>In the original project every agent was a separate process connected to a
 * remote broker. Here the broker is in memory ({@link alloverdev.broker.Connection})
 * and this class starts the Supervisor and then the other agents, in the
 * order suggested by the project documentation.</p>
 *
 * <pre>
 * java -jar bank-robbery.jar [--clients N] [--employees N] [--thieves N] [--guards N]
 *                            [--wallet EURO] [--neighborhood N] [--delay MS]
 *                            [--workdir DIR] [--verbose] [--debug]
 * </pre>
 */
public class Main {

    private static final String PROPERTIES = "simulation.properties";
    private static final String MAP_FILE = "Matrice_mappa.txt";

    private int clients = 3;
    private int employees = 2;
    private int thieves = 2;
    private int guards = 1;
    private int wallet = 0;
    private int neighborhood = 5;
    private long delayMs = 1500;
    private boolean debug = false;
    private boolean verbose = false;
    private String workdir = null;

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        if (!main.parse(args)) {
            usage();
            return;
        }
        File work = main.workdir != null ? new File(main.workdir) : findWorkDir();
        if (work == null) {
            System.err.println("Cannot find the folder containing " + MAP_FILE + ": pass it with --workdir <folder>.");
            return;
        }
        Assets.setBase(work);
        if (!main.verbose) {
            // Agents print a lot to the console: discard it unless asked
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        }
        main.run();
    }

    private boolean parse(String[] args) {
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--clients":
                        clients = Integer.parseInt(args[++i]);
                        break;
                    case "--employees":
                        employees = Integer.parseInt(args[++i]);
                        break;
                    case "--thieves":
                        thieves = Integer.parseInt(args[++i]);
                        break;
                    case "--guards":
                        guards = Integer.parseInt(args[++i]);
                        break;
                    case "--wallet":
                        wallet = Integer.parseInt(args[++i]);
                        break;
                    case "--neighborhood":
                        neighborhood = Integer.parseInt(args[++i]);
                        break;
                    case "--delay":
                        delayMs = Long.parseLong(args[++i]);
                        break;
                    case "--workdir":
                        workdir = args[++i];
                        break;
                    case "--verbose":
                        verbose = true;
                        break;
                    case "--debug":
                        debug = true;
                        verbose = true;
                        break;
                    default:
                        return false;
                }
            }
        } catch (RuntimeException ex) {
            return false;
        }
        return clients >= 0 && employees >= 0 && thieves >= 0 && guards >= 0 && wallet >= 0
                && neighborhood >= 3 && neighborhood < 29 && neighborhood % 2 == 1;
    }

    private static void usage() {
        System.out.println("Usage: java -jar bank-robbery.jar [options]");
        System.out.println("  --clients N       number of clients (default 3)");
        System.out.println("  --employees N     number of employees (default 2, 5 counters max)");
        System.out.println("  --thieves N       number of thieves (default 2)");
        System.out.println("  --guards N        number of guards (default 1)");
        System.out.println("  --wallet EURO     starting money in the clients' wallet (default 0)");
        System.out.println("  --neighborhood N  neighbourhood size, odd number between 3 and 27 (default 5)");
        System.out.println("  --delay MS        pause between starting two agents (default 1500)");
        System.out.println("  --workdir DIR     folder with maps and images (default: work)");
        System.out.println("  --verbose         print the agents' logs to the console");
        System.out.println("  --debug           like --verbose, plus every message exchanged");
    }

    private void run() throws InterruptedException {
        // A terminated agent stops its threads by throwing AgentTerminated: not an error
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (!(e instanceof AgentTerminated)) {
                System.err.println("Exception in thread " + t.getName() + ":");
                e.printStackTrace();
            }
        });

        start("Supervisor", conn -> new Supervisor(conn, neighborhood));
        Thread.sleep(delayMs * 2); // give the map time to be drawn

        // Recommended order: employees and clients alternately, then guards, then thieves
        List<Runnable> queue = new ArrayList<>();
        int max = Math.max(clients, employees);
        for (int i = 1; i <= max; i++) {
            final int n = i;
            if (i <= employees) {
                queue.add(() -> start("Employee" + n, Employee::new));
            }
            if (i <= clients) {
                queue.add(() -> start("Client" + n, conn -> new Client(conn, wallet)));
            }
        }
        for (int i = 1; i <= guards; i++) {
            final int n = i;
            queue.add(() -> start("Guard" + n, Guard::new));
        }
        for (int i = 1; i <= thieves; i++) {
            final int n = i;
            queue.add(() -> start("Thief" + n, Thief::new));
        }
        for (Runnable r : queue) {
            r.run();
            Thread.sleep(delayMs);
        }
    }

    private interface AgentFactory {

        AgentsIF create(Connection conn);
    }

    private void start(String login, AgentFactory factory) {
        Connection conn = new Connection(PROPERTIES, login, debug);
        AgentsIF agent = factory.create(conn);
        agent.subscribe(true);  // clear any previous registration
        agent.subscribe(false); // register the agent's activities
        Thread t = new Thread((Agent) agent, login);
        t.start();
        System.err.println("Started " + login + " (" + agent.getType() + ")");
    }

    /**
     * Looks for the folder with maps and images: the working directory itself,
     * {@code ./work}, or {@code work} next to (or above) the jar.
     */
    private static File findWorkDir() {
        File here = new File(".").getAbsoluteFile();
        File jarDir = here;
        try {
            jarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (URISyntaxException | RuntimeException ex) {
            // keep the current directory
        }
        File[] candidates = {
            here,
            new File(here, "work"),
            new File(jarDir, "work"),
            new File(jarDir.getParentFile(), "work")
        };
        for (File dir : candidates) {
            if (new File(dir, MAP_FILE).isFile()) {
                return dir;
            }
        }
        return null;
    }
}
