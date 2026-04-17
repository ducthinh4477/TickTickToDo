package hcmute.edu.vn.tickticktodo.agent.tools;

import android.content.Intent;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.model.CountdownEvent;
import hcmute.edu.vn.tickticktodo.ui.countdown.TimerService;

public class StartPomodoroTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.START_POMODORO_TOOL;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Start a pomodoro focus session and optionally create a countdown marker event.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject minutesSchema = new JSONObject();
        safePut(minutesSchema, "type", "integer");
        safePut(minutesSchema, "minimum", 1);
        safePut(minutesSchema, "maximum", 180);
        safePut(minutesSchema, "default", 25);
        safePut(properties, "minutes", minutesSchema);

        JSONObject createCountdownSchema = new JSONObject();
        safePut(createCountdownSchema, "type", "boolean");
        safePut(createCountdownSchema, "default", false);
        safePut(properties, "createCountdownEvent", createCountdownSchema);

        JSONObject countdownTitleSchema = new JSONObject();
        safePut(countdownTitleSchema, "type", "string");
        safePut(properties, "countdownTitle", countdownTitleSchema);

        safePut(parameters, "properties", properties);

        JSONArray required = new JSONArray();
        safePut(parameters, "required", required);

        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        int minutes = clamp(args.optInt("minutes", 25), 1, 180);
        boolean createCountdownEvent = args.optBoolean("createCountdownEvent", false);
        long startAtMillis = context.getNowMillis();
        long expectedFinishAtMillis = startAtMillis + (minutes * 60L * 1000L);

        String countdownTitle = args.optString("countdownTitle", "").trim();
        if (countdownTitle.isEmpty()) {
            countdownTitle = "Pomodoro " + minutes + "m";
        }

        try {
            Intent startIntent = new Intent(context.getApplication(), TimerService.class);
            startIntent.setAction(TimerService.ACTION_START);
            startIntent.putExtra(TimerService.EXTRA_START_MINUTES, minutes);

            try {
                ContextCompat.startForegroundService(context.getApplication(), startIntent);
            } catch (Exception firstStartError) {
                context.getApplication().startService(startIntent);
            }

            boolean countdownCreated = false;
            if (createCountdownEvent) {
                CountdownEvent countdownEvent = new CountdownEvent(countdownTitle, expectedFinishAtMillis);
                context.getDatabase().countdownEventDao().insert(countdownEvent);
                countdownCreated = true;
            }

            JSONObject result = new JSONObject();
            safePut(result, "minutes", minutes);
            safePut(result, "timerStarted", true);
            safePut(result, "startAtMillis", startAtMillis);
            safePut(result, "expectedFinishAtMillis", expectedFinishAtMillis);
            safePut(result, "countdownEventCreated", countdownCreated);
            safePut(result, "countdownTitle", countdownCreated ? countdownTitle : "");

            return ToolResult.success(call.getCallId(), getName(), result);
        } catch (Exception e) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "START_POMODORO_FAILED",
                    e.getMessage() == null ? "Không thể bắt đầu Pomodoro lúc này." : e.getMessage()
            );
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}