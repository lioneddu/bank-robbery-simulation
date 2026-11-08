package alloverdev.broker;

/**
 * Thrown in the threads of an agent whose connection has been closed, so that
 * they stop without bringing down the whole JVM. It extends {@link Error} on
 * purpose: the agents' code catches {@code Exception} in several places and must
 * not swallow it.
 */
public final class AgentTerminated extends Error {

    private static final long serialVersionUID = 1L;

    public AgentTerminated(String login) {
        super("Agent " + login + " terminated", null, false, false);
    }
}
