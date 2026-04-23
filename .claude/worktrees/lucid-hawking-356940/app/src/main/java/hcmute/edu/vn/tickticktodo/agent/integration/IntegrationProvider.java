package hcmute.edu.vn.doinbot.agent.integration;

import java.util.Collections;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.integration.model.ExternalDeadline;
import hcmute.edu.vn.doinbot.agent.integration.model.ExternalEvent;
import hcmute.edu.vn.doinbot.agent.integration.model.HealthSummary;

public interface IntegrationProvider {

    String getProviderName();

    default List<ExternalEvent> getEvents(long fromMillis, long toMillis) {
        return Collections.emptyList();
    }

    default List<ExternalDeadline> getDeadlines(long fromMillis, long toMillis) {
        return Collections.emptyList();
    }

    default HealthSummary getHealthSummary(long fromMillis, long toMillis) {
        return HealthSummary.unavailable(getProviderName());
    }
}