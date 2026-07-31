package com.ai.bovenko.assistant;

import java.util.HashMap;
import java.util.Map;

/**
 * Доменная модель черновика изменений (Change Set) для паттерна Controlled Write.
 */
public class ChangeSet {
    private final String id;
    private String status; // proposed, confirmed, applied, rejected, failed
    private final String operation;
    private final Map<String, String> payload = new HashMap<>();
    private final Map<String, Boolean> checks = new HashMap<>();
    private final boolean requiresUserConfirmation = true;

    public ChangeSet(String id, String operation) {
        this.id = id;
        this.status = "proposed";
        this.operation = operation;
    }

    public String getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperation() { return operation; }
    public Map<String, String> getPayload() { return payload; }
    public Map<String, Boolean> getChecks() { return checks; }

    /**
     * Сериализация черновика в JSON-строку вручную.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"change_set_id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"status\":\"").append(escapeJson(status)).append("\",");
        sb.append("\"operation\":\"").append(escapeJson(operation)).append("\",");
        
        // Payload block
        sb.append("\"payload\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
        }
        sb.append("},");

        // Checks block
        sb.append("\"checks\":{");
        first = true;
        for (Map.Entry<String, Boolean> entry : checks.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":")
              .append(entry.getValue());
        }
        sb.append("},");

        sb.append("\"requires_user_confirmation\":").append(requiresUserConfirmation);
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}