package hcmute.edu.vn.doinbot.agent.context;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.core.AgentEventBus;
import hcmute.edu.vn.doinbot.helper.AppRuntimeState;

public class ContextAgent {

    private static volatile ContextAgent INSTANCE;

    private final Context appContext;
    private volatile ContextSnapshot latestSnapshot;

    private ContextAgent(Context context) {
        this.appContext = context.getApplicationContext();
        this.latestSnapshot = buildSnapshot();
    }

    public static ContextAgent getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ContextAgent.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContextAgent(context);
                }
            }
        }
        return INSTANCE;
    }

    public ContextSnapshot getLatestSnapshot() {
        ContextSnapshot snapshot = latestSnapshot;
        if (snapshot == null) {
            snapshot = refreshSnapshot("LAZY_INIT");
        }
        return snapshot;
    }

    public synchronized ContextSnapshot refreshSnapshot(String reason) {
        ContextSnapshot snapshot = buildSnapshot();
        latestSnapshot = snapshot;

        JSONObject payload = new JSONObject();
        safePut(payload, "reason", reason == null ? "UNKNOWN" : reason);
        safePut(payload, "snapshot", snapshot.toCompactJson());
        AgentEventBus.getInstance().publish(
                AgentEvent.now(AgentEvent.TYPE_CONTEXT_REFRESHED, "ContextAgent", payload)
        );

        return snapshot;
    }

    private ContextSnapshot buildSnapshot() {
        long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();

        int batteryLevel = -1;
        boolean charging = false;

        try {
            Intent batteryIntent = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    batteryLevel = Math.round((level * 100f) / scale);
                }

                int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception ignored) {
        }

        String connectivity = getConnectivity();
        AppRuntimeState.Snapshot appState = AppRuntimeState.getSnapshot(appContext);

        return new ContextSnapshot(
                resolveTimeOfDay(calendar.get(Calendar.HOUR_OF_DAY)),
                calendar.get(Calendar.DAY_OF_WEEK),
                batteryLevel,
                charging,
                connectivity,
                appState.appInForeground,
                now
        );
    }

    private String getConnectivity() {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return "UNKNOWN";
            }

            Network activeNetwork = manager.getActiveNetwork();
            if (activeNetwork == null) {
                return "OFFLINE";
            }

            NetworkCapabilities capabilities = manager.getNetworkCapabilities(activeNetwork);
            if (capabilities == null) {
                return "CONNECTED";
            }

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "WIFI";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "CELLULAR";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "ETHERNET";
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                return "BLUETOOTH";
            }
            return "CONNECTED";
        } catch (SecurityException securityException) {
            return "UNKNOWN";
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private String resolveTimeOfDay(int hourOfDay) {
        if (hourOfDay >= 5 && hourOfDay < 11) {
            return "MORNING";
        }
        if (hourOfDay >= 11 && hourOfDay < 14) {
            return "MIDDAY";
        }
        if (hourOfDay >= 14 && hourOfDay < 18) {
            return "AFTERNOON";
        }
        if (hourOfDay >= 18 && hourOfDay < 22) {
            return "EVENING";
        }
        return "NIGHT";
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
