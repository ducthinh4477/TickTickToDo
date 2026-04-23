package hcmute.edu.vn.doinbot.core.background.assistant;

import androidx.annotation.Nullable;

public class AssistantStateMonitor {

    public enum AssistantState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING
    }

    private AssistantState currentState = AssistantState.IDLE;

    public boolean transitionTo(@Nullable AssistantState newState) {
        if (newState == null || currentState == newState) {
            return false;
        }
        currentState = newState;
        return true;
    }

    public AssistantState getCurrentState() {
        return currentState;
    }

    public void reset() {
        currentState = AssistantState.IDLE;
    }
}
