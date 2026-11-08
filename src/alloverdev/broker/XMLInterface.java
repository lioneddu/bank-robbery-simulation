package alloverdev.broker;

import java.util.Hashtable;

/**
 * Flat property view of a message element (status or task), compatible with
 * the API originally used by the agents.
 */
public class XMLInterface extends Hashtable<String, String> {

    private static final long serialVersionUID = 1L;

    /**
     * @return the value of the given property, or {@code null} if missing.
     */
    public String getProperty(String name) {
        return get(name);
    }

    void set(String name, String value) {
        if (name != null && value != null) {
            put(name, value);
        }
    }
}
