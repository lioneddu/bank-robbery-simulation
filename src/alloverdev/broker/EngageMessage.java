package alloverdev.broker;

/**
 * Asks the broker for the next activity assigned to the agent. On success the
 * task (Activity, Serial, Scheduler, Payload) is available via {@link #getTask()}.
 */
public class EngageMessage extends AbstractMessage {

    private XMLInterface task = new XMLInterface();

    public EngageMessage() {
    }

    public XMLInterface getTask() {
        return task;
    }

    void setTask(Task t) {
        task = new XMLInterface();
        task.set("Activity", t.activity);
        task.set("Serial", t.serial);
        task.set("Scheduler", t.scheduler);
        task.set("Payload", t.payload);
    }

    @Override
    public String getXMLMessage() {
        return "<Engage/>";
    }

    @Override
    void appendReplyBody(StringBuilder sb) {
        sb.append("<Task");
        task.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
        sb.append("/>");
    }

    @Override
    void clearContent() {
        task = new XMLInterface();
    }
}
