package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.marketing.application.internal.MarketingDeliveryEventService.ParsedEvent;
import za.co.handyflow.platform.shared.HandyFlowException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates and parses provider webhooks, then hands each event to
 * {@link MarketingDeliveryEventService}. Fails CLOSED: with no secret configured, nothing is accepted.
 *
 * Supported: generic (our own normalised JSON), postmark, mailgun.
 * Each event is applied in its own transaction, so one bad event can't block the rest.
 * Add SES/SendGrid by adding a parser here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingWebhookService {

    private final ObjectMapper objectMapper;
    private final MarketingDeliveryEventService eventService;

    @Value("${marketing.webhook.secret:}")
    private String sharedSecret;

    @Value("${marketing.webhook.mailgun-signing-key:}")
    private String mailgunSigningKey;

    public record WebhookResult(int received, int applied, int duplicates, int unmatched, int ignored, int failed) {}

    public WebhookResult handle(String provider, String rawBody, String secretHeader) {
        String p = provider == null ? "" : provider.toLowerCase();
        if (!List.of("generic", "postmark", "mailgun").contains(p)) {
            throw new HandyFlowException("Unsupported webhook provider", HttpStatus.NOT_FOUND, "WEBHOOK_PROVIDER_UNKNOWN");
        }
        if (!p.equals("mailgun")) requireSharedSecret(secretHeader);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new HandyFlowException("Malformed JSON", HttpStatus.BAD_REQUEST, "WEBHOOK_BAD_PAYLOAD");
        }
        if (root == null || root.isMissingNode()) {
            throw new HandyFlowException("Empty payload", HttpStatus.BAD_REQUEST, "WEBHOOK_BAD_PAYLOAD");
        }

        List<ParsedEvent> events = switch (p) {
            case "generic"  -> parseGeneric(root);
            case "postmark" -> parsePostmark(root);
            default -> { verifyMailgun(root); yield parseMailgun(root); }
        };

        int applied = 0, dup = 0, unmatched = 0, ignored = 0, failed = 0;
        for (ParsedEvent ev : events) {
            try {
                switch (eventService.ingest(p, ev)) {
                    case APPLIED   -> applied++;
                    case DUPLICATE -> dup++;
                    case UNMATCHED -> unmatched++;
                    case IGNORED   -> ignored++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to apply {} event {} from {}: {}", ev.type(), ev.providerEventId(), p, e.getMessage(), e);
            }
        }
        return new WebhookResult(events.size(), applied, dup, unmatched, ignored, failed);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    private void requireSharedSecret(String header) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new HandyFlowException("Webhook receiver is not configured", HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED");
        }
        if (header == null || !constantTimeEquals(sharedSecret, header)) {
            throw new HandyFlowException("Invalid webhook credentials", HttpStatus.UNAUTHORIZED, "WEBHOOK_UNAUTHORIZED");
        }
    }

    private void verifyMailgun(JsonNode root) {
        if (mailgunSigningKey == null || mailgunSigningKey.isBlank()) {
            throw new HandyFlowException("Webhook receiver is not configured", HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED");
        }
        JsonNode sig = root.path("signature");
        String timestamp = sig.path("timestamp").asText("");
        String token     = sig.path("token").asText("");
        String signature = sig.path("signature").asText("");
        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - ts) > 900) {   // replay window: 15 minutes
                throw new HandyFlowException("Stale webhook", HttpStatus.UNAUTHORIZED, "WEBHOOK_UNAUTHORIZED");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(mailgunSigningKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal((timestamp + token).getBytes(StandardCharsets.UTF_8)));
            if (!constantTimeEquals(expected, signature)) {
                throw new HandyFlowException("Invalid webhook signature", HttpStatus.UNAUTHORIZED, "WEBHOOK_UNAUTHORIZED");
            }
        } catch (HandyFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new HandyFlowException("Invalid webhook signature", HttpStatus.UNAUTHORIZED, "WEBHOOK_UNAUTHORIZED");
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    /** {"events":[{"id","type","campaignContactId","email","occurredAt","detail"}]} or a single such object. */
    private List<ParsedEvent> parseGeneric(JsonNode root) {
        List<ParsedEvent> out = new ArrayList<>();
        JsonNode arr = root.has("events") ? root.get("events") : null;
        if (arr != null && arr.isArray()) arr.forEach(n -> out.add(genericEvent(n)));
        else out.add(genericEvent(root));
        return out;
    }

    private ParsedEvent genericEvent(JsonNode n) {
        return new ParsedEvent(text(n, "id"), upper(text(n, "type")), uuid(text(n, "campaignContactId")),
                text(n, "email"), instant(text(n, "occurredAt")), text(n, "detail"));
    }

    /** Postmark sends one event per POST. Correlation id travels in Metadata.cc (X-PM-Metadata-cc header). */
    private List<ParsedEvent> parsePostmark(JsonNode n) {
        String recordType = n.path("RecordType").asText("");
        UUID cc = uuid(n.path("Metadata").path("cc").asText(null));
        switch (recordType) {
            case "Delivery":
                return List.of(new ParsedEvent("pm-delivery-" + n.path("MessageID").asText(), "DELIVERED", cc,
                        n.path("Recipient").asText(null), instant(n.path("DeliveredAt").asText(null)), null));
            case "Bounce": {
                String type = n.path("Type").asText("");
                boolean hard = type.equals("HardBounce") || type.equals("BadEmailAddress");
                return List.of(new ParsedEvent("pm-bounce-" + n.path("ID").asText(), hard ? "HARD_BOUNCE" : "SOFT_BOUNCE", cc,
                        n.path("Email").asText(null), instant(n.path("BouncedAt").asText(null)),
                        type + ": " + n.path("Description").asText("")));
            }
            case "SpamComplaint":
                return List.of(new ParsedEvent("pm-complaint-" + n.path("ID").asText(), "COMPLAINT", cc,
                        n.path("Email").asText(null), instant(n.path("BouncedAt").asText(null)), "Spam complaint"));
            default:
                return List.of();
        }
    }

    /** Mailgun: correlation id travels in event-data.user-variables.cc (X-Mailgun-Variables header). */
    private List<ParsedEvent> parseMailgun(JsonNode root) {
        JsonNode d = root.path("event-data");
        String event = d.path("event").asText("");
        String type = switch (event) {
            case "delivered"    -> "DELIVERED";
            case "complained"   -> "COMPLAINT";
            case "unsubscribed" -> "UNSUBSCRIBE";
            case "failed"       -> "permanent".equals(d.path("severity").asText("")) ? "HARD_BOUNCE" : "SOFT_BOUNCE";
            default -> null;
        };
        if (type == null) return List.of();
        Instant at = d.has("timestamp") ? Instant.ofEpochMilli((long) (d.path("timestamp").asDouble() * 1000)) : null;
        String detail = d.path("delivery-status").path("message").asText(d.path("reason").asText(null));
        return List.of(new ParsedEvent("mg-" + d.path("id").asText(), type,
                uuid(d.path("user-variables").path("cc").asText(null)),
                d.path("recipient").asText(null), at, detail));
    }

    // ── Small helpers ─────────────────────────────────────────────────────────

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }
    private static String upper(String s) { return s == null ? null : s.trim().toUpperCase(); }
    private static UUID uuid(String s) {
        try { return s == null || s.isBlank() ? null : UUID.fromString(s.trim()); } catch (Exception e) { return null; }
    }
    private static Instant instant(String s) {
        try { return s == null || s.isBlank() ? null : Instant.parse(s); } catch (Exception e) { return null; }
    }
}