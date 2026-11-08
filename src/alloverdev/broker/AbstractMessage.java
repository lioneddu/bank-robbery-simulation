package alloverdev.broker;

/**
 * Base class of every message block exchanged with the (local) broker.
 * Each message follows a request/reply pattern: after
 * {@link Connection#send(AbstractMessage)} the reply status is available via
 * {@link #getStatus()}.
 */
public abstract class AbstractMessage {

    private XMLInterface status = new XMLInterface();

    /**
     * @return a human readable description of the request.
     */
    public abstract String getXMLMessage();

    /**
     * @return a human readable description of the reply.
     */
    public final String getXMLReply() {
        StringBuilder sb = new StringBuilder("<Reply><Status");
        status.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
        sb.append("/>");
        appendReplyBody(sb);
        return sb.append("</Reply>").toString();
    }

    /**
     * @return the status of the last transaction ({@code code} = OK | KO | RETRY).
     */
    public final XMLInterface getStatus() {
        return status;
    }

    /**
     * Resets the message block so that it can be reused.
     */
    public final void clear() {
        status = new XMLInterface();
        clearContent();
    }

    void setStatus(String code, String message) {
        status = new XMLInterface();
        status.set("code", code);
        status.set("message", message);
    }

    void appendReplyBody(StringBuilder sb) {
    }

    void clearContent() {
    }
}
