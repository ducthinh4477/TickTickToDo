package hcmute.edu.vn.tickticktodo.agent.integration.providers;

import android.content.Context;

import hcmute.edu.vn.tickticktodo.agent.context.ContextAgent;
import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.integration.IntegrationProvider;
import hcmute.edu.vn.tickticktodo.agent.integration.model.HealthSummary;

public class HealthFallbackProvider implements IntegrationProvider {

    private final Context appContext;

    public HealthFallbackProvider(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public String getProviderName() {
        return "LOCAL_HEURISTIC";
    }

    @Override
    public HealthSummary getHealthSummary(long fromMillis, long toMillis) {
        ContextSnapshot snapshot = ContextAgent.getInstance(appContext).getLatestSnapshot();
        if (snapshot == null) {
            return HealthSummary.unavailable(getProviderName());
        }

        String energy = HealthSummary.ENERGY_MEDIUM;
        int battery = snapshot.getBatteryLevel();
        if (battery >= 0 && battery <= 20 && !snapshot.isCharging()) {
            energy = HealthSummary.ENERGY_LOW;
        } else if (snapshot.isCharging() && "MORNING".equals(snapshot.getTimeOfDay())) {
            energy = HealthSummary.ENERGY_HIGH;
        }

        return new HealthSummary(
                false,
                0f,
                0,
                0,
                energy,
                getProviderName()
        );
    }
}