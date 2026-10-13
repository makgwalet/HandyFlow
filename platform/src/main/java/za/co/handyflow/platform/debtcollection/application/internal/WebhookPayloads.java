package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the NORMALISED event shapes the webhook endpoints accept (a provider-specific translation layer maps a
 * provider's own format onto these). Pure over Jackson trees. A single request may be one event object or
 * {"events":[...]}.
 * <pre>
 *  DELIVERY_STATUS: {"messageId":"abc","status":"DELIVERED|SENT|FAILED|BOUNCED|RETURNED","detail":"...","timestamp":"2026-10-10T08:31:00Z"}
 *  INBOUND_MESSAGE: {"messageId":"abc","channel":"SMS","from":"+27821234567","body":"text","timestamp":"..."}
 *  PAYMENT:         {"reference":"BANK123","amount":"1500.00","currency":"ZAR","date":"2026-10-10","status":"COMPLETE",
 *                    "caseNumber":"DC-00042","payerReference":"INV DC-00042","source":"EFT"}
 * </pre>
 */
final class WebhookPayloads {

    enum PaymentKind { PAYMENT, REVERSAL, IGNORE }

    record DeliveryEvent(String messageId, CommunicationStatus status, String detail, Instant at) {}

    record InboundEvent(String messageId, CommunicationChannel channel, String from, String body, Instant at) {}

    record PaymentEvent(String reference, BigDecimal amount, String currency, LocalDate date, String caseNumber,
                        String payerReference, PaymentSource source, PaymentKind kind, String rawStatus) {}

    private static final Pattern CASE_NUMBER = Pattern.compile("\\bDC-\\d{5,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PAID = Set.of("COMPLETE", "COMPLETED", "PAID", "SUCCESS", "SUCCESSFUL");
    private static final Set<String> REVERSED = Set.of("REVERSED", "CHARGEBACK", "BOUNCED", "RETURNED", "REFUNDED");

    private WebhookPayloads() {}

    static DeliveryEvent delivery(JsonNode n) {
        String id = text(n, "messageId");
        if (id == null) throw new IllegalArgumentException("messageId is required");
        CommunicationStatus s;
        try {
            s = CommunicationStatus.valueOf(required(n, "status").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return new DeliveryEvent(id, null, text(n, "detail"), instant(n));
        }
        boolean supported = s == CommunicationStatus.SENT || s == CommunicationStatus.DELIVERED
                || s == CommunicationStatus.FAILED || s == CommunicationStatus.BOUNCED || s == CommunicationStatus.RETURNED;
        return new DeliveryEvent(id, supported ? s : null, text(n, "detail"), instant(n));
    }

    static InboundEvent inbound(JsonNode n) {
        CommunicationChannel ch;
        try {
            ch = CommunicationChannel.valueOf(required(n, "channel").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("channel must be SMS, EMAIL, LETTER or WHATSAPP");
        }
        return new InboundEvent(text(n, "messageId"), ch, required(n, "from"), required(n, "body"), instant(n));
    }

    static PaymentEvent payment(JsonNode n) {
        String reference = required(n, "reference");
        BigDecimal amount;
        try {
            amount = new BigDecimal(required(n, "amount"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount is not a number");
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        String rawStatus = text(n, "status") == null ? "COMPLETE" : text(n, "status");
        String payerRef = text(n, "payerReference");
        String caseNumber = text(n, "caseNumber") != null ? text(n, "caseNumber").trim().toUpperCase(Locale.ROOT)
                : extractCaseNumber(payerRef);
        LocalDate date = null;
        if (text(n, "date") != null) {
            try {
                date = LocalDate.parse(text(n, "date"));
            } catch (Exception e) {
                throw new IllegalArgumentException("date must be ISO yyyy-MM-dd");
            }
        }
        return new PaymentEvent(reference, amount.setScale(2, java.math.RoundingMode.HALF_UP),
                text(n, "currency") == null ? null : text(n, "currency").toUpperCase(Locale.ROOT), date, caseNumber, payerRef,
                source(text(n, "source")), classify(rawStatus), rawStatus);
    }

    static PaymentKind classify(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (PAID.contains(s)) return PaymentKind.PAYMENT;
        if (REVERSED.contains(s)) return PaymentKind.REVERSAL;
        return PaymentKind.IGNORE;
    }

    static String extractCaseNumber(String text) {
        if (text == null) return null;
        Matcher m = CASE_NUMBER.matcher(text);
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : null;
    }

    private static PaymentSource source(String s) {
        if (s == null) return PaymentSource.GATEWAY;
        try {
            return PaymentSource.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PaymentSource.GATEWAY;
        }
    }

    private static Instant instant(JsonNode n) {
        String t = text(n, "timestamp");
        if (t == null) return null;
        try {
            return Instant.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static String required(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " is required");
        return v;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
