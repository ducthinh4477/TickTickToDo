package hcmute.edu.vn.tickticktodo.agent;

import org.json.JSONArray;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import hcmute.edu.vn.tickticktodo.agent.tools.FindTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.CreateTaskWithSubtasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.GetOverdueTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.GetTodayTasksTool;
import hcmute.edu.vn.tickticktodo.agent.tools.RescheduleBulkTasksTool;

public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public static AgentToolRegistry withDefaultQueryTools() {
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.register(new GetTodayTasksTool());
        registry.register(new GetOverdueTasksTool());
        registry.register(new FindTasksTool());
        registry.register(new CreateTaskWithSubtasksTool());
        registry.register(new RescheduleBulkTasksTool());
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
