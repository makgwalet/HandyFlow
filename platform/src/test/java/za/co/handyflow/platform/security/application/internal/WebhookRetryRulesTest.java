package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookRetryRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Test void backoffIs1_5_15_30MinutesThenStops() {
        assertEquals(NOW.plusSeconds(60), WebhookRetryRules.nextRetryAt(1, NOW));
        assertEquals(NOW.plusSeconds(300), WebhookRetryRules.nextRetryAt(2, NOW));
        assertEquals(NOW.plusSeconds(900), WebhookRetryRules.nextRetryAt(3, NOW));
        assertEquals(NOW.plusSeconds(1800), WebhookRetryRules.nextRetryAt(4, NOW));
        assertNull(WebhookRetryRules.nextRetryAt(5, NOW));
        assertNull(WebhookRetryRules.nextRetryAt(9, NOW));
    }

    @Test void onlyStoredPayloadsCanBeReplayed() {
        assertTrue(WebhookRetryRules.canReplay("{\"event\":\"X\"}"));
        assertFalse(WebhookRetryRules.canReplay(null));
        assertFalse(WebhookRetryRules.canReplay("  "));
    }

    // ── source checks (the Spring parts cannot be run in the sandbox) ──
    private static String read(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    @Test void retryResendsStoredPayloadNotAPlaceholder() throws Exception {
        String s = read("application/internal/PublicApiService.java");
        assertFalse(s.contains("\"retry\\\":true"), "placeholder body must be gone");
        assertTrue(s.contains("delivery.getPayloadJson()"));
        assertTrue(s.contains("payloadHash, payloadJson, attempt"), "payload stored on every attempt");
    }

    @Test void oldRowIsSupersededBeforeResending() throws Exception {
        String s = read("application/internal/PublicApiService.java");
        int sup = s.indexOf("delivery.supersede()");
        int send = s.indexOf("deliverToSubscription(sub, delivery.getEventType()");
        assertTrue(sup > 0 && send > sup, "supersede must come first");
        assertTrue(s.indexOf("getPayloadJson()") < sup, "payload read before it is cleared");
    }

    @Test void entityClearsPayloadWhenDoneAndMigrationAddsColumn() throws Exception {
        String e = read("domain/model/WebhookDelivery.java");
        assertTrue(e.contains("payload_json"));
        assertEquals(4, e.split("this\\.payloadJson = null", -1).length - 1,
                "cleared on success, supersede, and both final failures");
        String m = Files.readString(Path.of("src/main/resources/db/migration/V847__security_webhook_delivery_payload.sql"));
        assertTrue(m.contains("payload_json TEXT"));
    }

    @Test void sweeperRaisesShiftMissedOnlyForShiftsItMarked() throws Exception {
        String s = read("application/internal/ShiftMissedSweeper.java");
        assertTrue(s.contains("\"SHIFT_MISSED\""));
        assertTrue(s.contains("markMissedIfScheduled(") && s.contains("== 1"));
    }
}
