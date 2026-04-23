package hcmute.edu.vn.tickticktodo.agent.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class AgentEventBus {

    public interface Listener {
        void onEvent(AgentEvent event);
    }

    private static final AgentEventBus INSTANCE = new AgentEventBus();

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private AgentEventBus() {
    }

    public static AgentEventBus getInstance() {
        return INSTANCE;
    }

    public void subscribeAll(String subscriberId, Listener listener) {
        subscribe(subscriberId, null, listener);
    }

    public void subscribe(String subscriberId, Set<String> eventTypes, Listener listener) {
        if (listener == null) {
            return;
        }

        String safeSubscriberId = sanitizeSubscriberId(subscriberId, listener);
        unsubscribe(safeSubscriberId);

        Set<String> typeFilter = eventTypes == null
                ? Collections.emptySet()
                : new HashSet<>(eventTypes);
        subscriptions.add(new Subscription(safeSubscriberId, typeFilter, listener));
    }

    public void unsubscribe(String subscriberId) {
        if (subscriberId == null || subscriberId.trim().isEmpty()) {
            return;
        }

        for (Subscription subscription : subscriptions) {
            if (subscription.subscriberId.equals(subscriberId)) {
                subscriptions.remove(subscription);
            }
        }
    }

    public void publish(AgentEvent event) {
        if (event == null) {
            return;
        }

        for (Subscription subscription : subscriptions) {
            if (!shouldDeliver(subscription, event)) {
                continue;
            }

            try {
                subscription.listener.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean shouldDeliver(Subscription subscription, AgentEvent event) {
        return subscription.eventTypes.isEmpty() || subscription.eventTypes.contains(event.getType());
    }

    private String sanitizeSubscriberId(String subscriberId, Listener listener) {
        if (subscriberId != null && !subscriberId.trim().isEmpty()) {
            return subscriberId.trim();
        }
        return "listener_" + listener.hashCode();
    }

    private static final class Subscription {
        final String subscriberId;
        final Set<String> eventTypes;
        final Listener listener;

        Subscription(String subscriberId, Set<String> eventTypes, Listener listener) {
            this.subscriberId = subscriberId;
            this.eventTypes = eventTypes;
            this.listener = listener;
        }
    }
}
