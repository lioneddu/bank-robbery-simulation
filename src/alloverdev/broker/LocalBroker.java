package alloverdev.broker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory replacement of the remote activity broker.
 *
 * <p>Every agent owns a mailbox. Notify messages schedule new activities that
 * are routed according to their scope:</p>
 * <ul>
 * <li>{@code ANY}: one subscribed agent able to perform the activity (the
 * least busy one, preferring agents other than the sender). If none is
 * available yet the task waits until a capable agent subscribes;</li>
 * <li>{@code ALL}: every subscribed capable agent except the sender;</li>
 * <li>a login: that specific agent (dropped if it has left the community).</li>
 * </ul>
 * Engage messages take the next task from the agent's own mailbox.
 */
final class LocalBroker {

    private static final LocalBroker INSTANCE = new LocalBroker();

    static LocalBroker get() {
        return INSTANCE;
    }

    private static final class Mailbox {

        final LinkedBlockingQueue<Task> queue = new LinkedBlockingQueue<>();
        final Set<String> capabilities = new HashSet<>();
        boolean subscribed;
    }

    private final Map<String, Mailbox> mailboxes = new HashMap<>();
    private final Set<String> departed = new HashSet<>();
    private final List<Task> pendingAny = new ArrayList<>();
    private final AtomicLong serials = new AtomicLong();
    private volatile boolean trace = Boolean.getBoolean("broker.trace");

    private LocalBroker() {
    }

    private Mailbox mailbox(String login) {
        return mailboxes.computeIfAbsent(login, k -> new Mailbox());
    }

    synchronized void subscribe(String login, Set<String> activities) {
        Mailbox box = mailbox(login);
        departed.remove(login);
        box.capabilities.clear();
        box.capabilities.addAll(activities);
        box.subscribed = true;
        log("SUBSCRIBE " + login + " " + activities);

        // Deliver tasks that were waiting for a capable agent
        for (Iterator<Task> it = pendingAny.iterator(); it.hasNext();) {
            Task t = it.next();
            if (box.capabilities.contains(t.activity)) {
                box.queue.add(t);
                it.remove();
            }
        }
    }

    synchronized void unsubscribe(String login) {
        Mailbox box = mailboxes.get(login);
        if (box != null) {
            box.subscribed = false;
            box.capabilities.clear();
            box.queue.clear();
        }
        departed.add(login);
        pendingAny.removeIf(t -> login.equals(t.scheduler));
        log("UNSUBSCRIBE " + login);
    }

    synchronized void schedule(String sender, String activity, String payload, String scope) {
        Task task = new Task(activity, Long.toString(serials.incrementAndGet()), sender, payload);

        if ("ALL".equals(scope)) {
            for (Map.Entry<String, Mailbox> e : mailboxes.entrySet()) {
                Mailbox box = e.getValue();
                if (box.subscribed && box.capabilities.contains(activity) && !e.getKey().equals(sender)) {
                    box.queue.add(task);
                }
            }
            log("ALL  " + task);

        } else if ("ANY".equals(scope)) {
            Mailbox best = null;
            boolean bestIsSender = true;
            for (Map.Entry<String, Mailbox> e : mailboxes.entrySet()) {
                Mailbox box = e.getValue();
                if (!box.subscribed || !box.capabilities.contains(activity)) {
                    continue;
                }
                boolean isSender = e.getKey().equals(sender);
                if (best == null
                        || (bestIsSender && !isSender)
                        || (bestIsSender == isSender && box.queue.size() < best.queue.size())) {
                    best = box;
                    bestIsSender = isSender;
                }
            }
            if (best != null) {
                best.queue.add(task);
            } else {
                pendingAny.add(task);
            }
            log("ANY  " + task);

        } else {
            if (departed.contains(scope)) {
                log("DROP " + task + " -> " + scope + " (departed)");
                return;
            }
            mailbox(scope).queue.add(task);
            log("TO   " + scope + " " + task);
        }
    }

    /**
     * Waits up to {@code timeoutMs} for the next task of the given agent.
     */
    Task take(String login, long timeoutMs) throws InterruptedException {
        LinkedBlockingQueue<Task> queue;
        synchronized (this) {
            queue = mailbox(login).queue;
        }
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void log(String s) {
        if (trace) {
            System.out.println("[broker] " + s);
        }
    }
}
