package alloverdev.broker;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Declares the activities (capabilities) an agent can perform. A subscribe
 * message without activities unsubscribes the agent.
 */
public class SubscribeMessage extends AbstractMessage {

    private final Set<String> activities = new LinkedHashSet<>();

    public SubscribeMessage() {
    }

    public void addActivity(String activity) {
        if (activity != null) {
            activities.add(activity);
        }
    }

    Set<String> getActivities() {
        return activities;
    }

    @Override
    public String getXMLMessage() {
        return "<Subscribe activities=\"" + String.join(",", activities) + "\"/>";
    }

    @Override
    void clearContent() {
        activities.clear();
    }
}
