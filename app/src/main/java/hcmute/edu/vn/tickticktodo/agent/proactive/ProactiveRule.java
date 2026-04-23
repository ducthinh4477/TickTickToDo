package hcmute.edu.vn.tickticktodo.agent.proactive;

import android.content.Context;

import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;

public interface ProactiveRule {

    String getRuleId();

    boolean supports(AgentEvent event);

    Suggestion evaluate(AgentEvent event, RuleContext context);

    final class RuleContext {
        private final Context appContext;
        private final TaskDatabase database;
        private final ContextSnapshot snapshot;
        private final long nowMillis;

        public RuleContext(Context appContext,
                           TaskDatabase database,
                           ContextSnapshot snapshot,
                           long nowMillis) {
            this.appContext = appContext;
            this.database = database;
            this.snapshot = snapshot;
            this.nowMillis = nowMillis;
        }

        public Context getAppContext() {
            return appContext;
        }

        public TaskDatabase getDatabase() {
            return database;
        }

        public ContextSnapshot getSnapshot() {
            return snapshot;
        }

        public long getNowMillis() {
            return nowMillis;
        }
    }
}
