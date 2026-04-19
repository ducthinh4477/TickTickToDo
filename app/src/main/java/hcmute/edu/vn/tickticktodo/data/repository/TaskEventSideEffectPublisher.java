package hcmute.edu.vn.tickticktodo.data.repository;

import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEventBus;
import hcmute.edu.vn.tickticktodo.model.Task;

final class TaskEventSideEffectPublisher {

    private final AgentEventBus eventBus;

    TaskEventSideEffectPublisher(AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    void publishTaskEvent(String eventType, Task task) {
        if (task == null) {
            return;
        }

        JSONObject payload = new JSONObject();
        safePut(payload, "taskId", task.getId());
        safePut(payload, "title", task.getTitle());
        safePut(payload, "dueDate", task.getDueDate());
        safePut(payload, "priority", task.getPriority());
        safePut(payload, "completed", task.isCompleted());
        safePut(payload, "source", task.getSource());
        eventBus.publish(AgentEvent.now(eventType, "TaskRepository", payload));
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}