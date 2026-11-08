package alloverdev.broker;

import java.awt.EventQueue;
import java.awt.Window;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connection of an agent to the in-memory broker ({@link LocalBroker}).
 *
 * <p>It keeps the original API (properties lookup, login, {@code send}) so the
 * agents run unchanged, but every agent now lives in the same JVM. For this
 * reason an agent must not call {@code System.exit}: it calls
 * {@link #terminate()} instead, which unsubscribes it, closes its windows and
 * stops its threads.</p>
 */
public class Connection {

    private static final long POLL_MS = 250;

    private final Properties props = new Properties();
    private final String login;
    private final boolean debug;
    private final List<Window> windows = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    public Connection(String propfile) {
        this(propfile, null, false);
    }

    public Connection(String propfile, boolean debug) {
        this(propfile, null, debug);
    }

    public Connection(String propfile, String ident) {
        this(propfile, ident, false);
    }

    /**
     * @param propfile properties file with the activity names. It is looked up
     * in the working directory first, then on the classpath.
     * @param ident agent identifier. If the properties define
     * {@code server_<ident>.login} that value is used as login, otherwise the
     * identifier itself is the login.
     * @param debug prints every message exchanged by this agent.
     */
    public Connection(String propfile, String ident, boolean debug) {
        loadProperties(propfile);
        String l = ident == null ? null : props.getProperty("server_" + ident + ".login");
        if (l == null) {
            l = ident != null ? ident : props.getProperty("server.login", "agent");
        }
        this.login = l.trim();
        this.debug = debug;
    }

    private void loadProperties(String propfile) {
        try {
            File f = new File(propfile);
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    props.load(in);
                    return;
                }
            }
            try (InputStream in = Connection.class.getResourceAsStream("/" + propfile)) {
                if (in != null) {
                    props.load(in);
                    return;
                }
            }
            try (InputStream in = Connection.class.getResourceAsStream("/simulation.properties")) {
                if (in != null) {
                    props.load(in);
                    return;
                }
            }
            System.err.println("Properties not found: " + propfile);
        } catch (IOException ex) {
            System.err.println("Error reading properties: " + ex);
        }
    }

    public String getLogin() {
        return login;
    }

    public String getProperty(String key) {
        String v = props.getProperty(key);
        return v == null ? null : v.trim();
    }

    public String getProperty(String key, String defaultvalue) {
        String v = getProperty(key);
        return v == null ? defaultvalue : v;
    }

    public void send(AbstractMessage msg) {
        send(msg, true);
    }

    /**
     * Sends a message block to the broker.
     *
     * @param blocking for an {@link EngageMessage}: wait until a task is
     * available (true) or return immediately with status RETRY (false).
     */
    public void send(AbstractMessage msg, boolean blocking) {
        ensureOpen();
        LocalBroker broker = LocalBroker.get();

        if (msg instanceof SubscribeMessage) {
            SubscribeMessage sub = (SubscribeMessage) msg;
            if (sub.getActivities().isEmpty()) {
                broker.unsubscribe(login);
            } else {
                broker.subscribe(login, sub.getActivities());
            }
            msg.setStatus("OK", null);

        } else if (msg instanceof NotifyMessage) {
            for (NotifyMessage.Next n : ((NotifyMessage) msg).getNext()) {
                broker.schedule(login, n.activity, n.payload, n.scope);
            }
            msg.setStatus("OK", null);

        } else if (msg instanceof EngageMessage) {
            Task t = null;
            try {
                do {
                    t = broker.take(login, blocking ? POLL_MS : 0);
                    ensureOpen();
                } while (t == null && blocking);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AgentTerminated(login);
            }
            if (t == null) {
                msg.setStatus("RETRY", null);
                msg.getStatus().set("delay", Long.toString(POLL_MS));
            } else {
                ((EngageMessage) msg).setTask(t);
                msg.setStatus("OK", null);
            }

        } else {
            msg.setStatus("KO", "Unsupported message block: " + msg.getClass().getName());
        }

        if (debug) {
            System.out.println("[" + login + "] " + msg.getXMLMessage() + " -> " + msg.getXMLReply());
        }
    }

    /**
     * Registers a window owned by this agent, closed by {@link #terminate()}.
     */
    public void registerWindow(Window w) {
        windows.add(w);
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Ends this agent only: unsubscribes it, disposes its windows and makes
     * every further call on this connection throw {@link AgentTerminated}. When
     * invoked from an agent thread it also stops the calling thread.
     */
    public void terminate() {
        if (!closed) {
            LocalBroker.get().unsubscribe(login);
            closed = true;
            for (Window w : windows) {
                EventQueue.invokeLater(w::dispose);
            }
            System.out.println("Agent " + login + " terminated");
        }
        if (!EventQueue.isDispatchThread()) {
            throw new AgentTerminated(login);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new AgentTerminated(login);
        }
    }
}
