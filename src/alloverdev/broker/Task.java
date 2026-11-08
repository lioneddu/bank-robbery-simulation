package alloverdev.broker;

/**
 * An activity instance waiting to be performed by an agent.
 */
final class Task {

    final String activity;
    final String serial;
    final String scheduler;
    final String payload;

    Task(String activity, String serial, String scheduler, String payload) {
        this.activity = activity;
        this.serial = serial;
        this.scheduler = scheduler;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return activity + "#" + serial + " from " + scheduler + " [" + payload + "]";
    }
}
