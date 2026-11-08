package alloverdev.broker;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifies the completion (or refusal) of an activity and schedules the next
 * activities to be performed by other agents.
 *
 * <p>Scope of a next activity: {@code ANY} (one capable agent), {@code ALL}
 * (every capable agent except the sender) or the login of a specific agent.</p>
 */
public class NotifyMessage extends AbstractMessage {

    static final class Next {

        final String activity;
        final String payload;
        final String scope;

        Next(String activity, String payload, String scope) {
            this.activity = activity;
            this.payload = payload;
            this.scope = scope == null ? "ANY" : scope;
        }
    }

    private String activity;
    private String serial;
    private String outcome;
    private String info;
    private final List<Next> next = new ArrayList<>();

    public NotifyMessage() {
    }

    public NotifyMessage(String activity, String serial, String outcome, String info) {
        this.activity = activity;
        this.serial = serial;
        this.outcome = outcome;
        this.info = info;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public void addNext(String activity) {
        addNext(activity, null, "ANY");
    }

    public void addNext(String activity, String payload) {
        addNext(activity, payload, "ANY");
    }

    public void addNext(String activity, String payload, String scope) {
        next.add(new Next(activity, payload, scope));
    }

    List<Next> getNext() {
        return next;
    }

    @Override
    public String getXMLMessage() {
        StringBuilder sb = new StringBuilder("<Notify activity=\"").append(activity)
                .append("\" serial=\"").append(serial)
                .append("\" outcome=\"").append(outcome)
                .append("\" info=\"").append(info).append("\">");
        for (Next n : next) {
            sb.append("<Next activity=\"").append(n.activity)
                    .append("\" scope=\"").append(n.scope)
                    .append("\">").append(n.payload).append("</Next>");
        }
        return sb.append("</Notify>").toString();
    }

    @Override
    void clearContent() {
        activity = serial = outcome = info = null;
        next.clear();
    }
}
