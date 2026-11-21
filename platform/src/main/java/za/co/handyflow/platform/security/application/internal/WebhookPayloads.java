package za.co.handyflow.platform.security.application.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Bodies sent to customers' webhook endpoints. Deliberately small and free of personal data:
 * ids, kind, severity and time. The receiver calls the API for anything more.
 */
final class WebhookPayloads {

    private WebhookPayloads() {}

    static String event(String eventType, UUID id, UUID siteId, String severity, String detail, Instant at) {
        return "{\"event\":" + q(eventType)
                + ",\"id\":" + q(id == null ? null : id.toString())
                + ",\"siteId\":" + q(siteId == null ? null : siteId.toString())
                + ",\"severity\":" + q(severity)
                + ",\"detail\":" + q(detail)
                + ",\"occurredAt\":" + q(at == null ? null : at.toString()) + "}";
    }

    static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }
}
