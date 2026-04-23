package hcmute.edu.vn.doinbot.agent.proactive;

import java.util.UUID;

import hcmute.edu.vn.doinbot.data.model.SuggestionEntity;

public class Suggestion {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_SHOWN = "SHOWN";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_APPLIED = "APPLIED";

    private final String id;
    private final String type;
    private final String title;
    private final String reason;
    private final float confidence;
    private final float priorityScore;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final boolean requiresConfirmation;
    private final String status;

    public Suggestion(String id,
                      String type,
                      String title,
                      String reason,
                      float confidence,
                      float priorityScore,
                      long createdAtMillis,
                      long expiresAtMillis,
                      boolean requiresConfirmation,
                      String status) {
        this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id;
        this.type = type == null ? "GENERAL" : type;
        this.title = title == null ? "" : title;
        this.reason = reason == null ? "" : reason;
        this.confidence = confidence;
        this.priorityScore = priorityScore;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.requiresConfirmation = requiresConfirmation;
        this.status = (status == null || status.trim().isEmpty()) ? STATUS_NEW : status;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getReason() {
        return reason;
    }

    public float getConfidence() {
        return confidence;
    }

    public float getPriorityScore() {
        return priorityScore;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public String getStatus() {
        return status;
    }

    public SuggestionEntity toEntity() {
        SuggestionEntity entity = new SuggestionEntity();
        entity.id = id;
        entity.type = type;
        entity.title = title;
        entity.reason = reason;
        entity.confidence = confidence;
        entity.priorityScore = priorityScore;
        entity.createdAtMillis = createdAtMillis;
        entity.expiresAtMillis = expiresAtMillis;
        entity.requiresConfirmation = requiresConfirmation;
        entity.status = status;
        return entity;
    }
}
