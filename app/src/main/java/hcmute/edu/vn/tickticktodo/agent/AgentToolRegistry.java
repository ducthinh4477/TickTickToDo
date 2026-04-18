package hcmute.edu.vn.tickticktodo.agent;

import org.json.JSONArray;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import hcmute.edu.vn.tickticktodo.agent.tools.FindTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.CreateTaskWithSubtasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.CompleteTaskTool;
import hcmute.edu.vn.tickticktodo.agent.tools.BreakdownTaskTool;
import hcmute.edu.vn.tickticktodo.agent.tools.EisenhowerSortTool;
import hcmute.edu.vn.tickticktodo.agent.tools.GetOverdueTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.GetTodayTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.RescheduleBulkTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.StartPomodoroTool;

public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public static AgentToolRegistry withDefaultQueryTools() {
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.register(new GetTodayTasksTool());
        registry.register(new GetOverdueTasksTool());
        registry.register(new FindTasksTool());
        registry.register(new CreateTaskWithSubtasksTool());
        registry.register(new CompleteTaskTool());
        registry.register(new RescheduleBulkTasksTool());
        registry.register(new StartPomodoroTool());
        registry.register(new EisenhowerSortTool());
        registry.register(new BreakdownTaskTool());
        // Keep backwards-compatible action names so model outputs like CREATE_TASK still execute tools.
        registry.registerAlias(AgentAction.CREATE_TASK, AgentToolNames.CREATE_TASK_WITH_SUBTASKS);
        registry.registerAlias(AgentAction.COMPLETE_TASK, AgentToolNames.COMPLETE_TASK_TOOL);
        registry.registerAlias(AgentAction.LIST_TODAY, AgentToolNames.GET_TODAY_TASKS);
        return registry;
    }

    public void register(AgentTool tool) {
        if (tool == null || tool.getName() == null) {
            return;
        }
        tools.put(normalize(tool.getName()), tool);
    }

    public AgentTool get(String toolName) {
        return tools.get(normalize(toolName));
    }

    public boolean contains(String toolName) {
        return tools.containsKey(normalize(toolName));
    }

    public Collection<AgentTool> allTools() {
        return tools.values();
    }

    public void registerAlias(String aliasName, String targetToolName) {
        AgentTool target = tools.get(normalize(targetToolName));
        if (target == null || aliasName == null || aliasName.trim().isEmpty()) {
            return;
        }
        tools.put(normalize(aliasName), target);
    }

    public JSONArray getToolSchemas() {
        JSONArray schemas = new JSONArray();
        for (AgentTool tool : tools.values()) {
            schemas.put(tool.getSchema());
        }
        return schemas;
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
