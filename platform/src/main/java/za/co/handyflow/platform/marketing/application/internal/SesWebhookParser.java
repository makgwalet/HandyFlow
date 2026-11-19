package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Handles Amazon SNS messages carrying SES events. Verifies the SNS signature before trusting anything. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SesWebhookParser {

    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${marketing.webhook.ses-topic-arns:}")
    private String topicArns;

    private static final Pattern SNS_HOST = Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?$");

    /** Returns the verified SNS envelope, after handling subscription confirmation. Notifications go to {@link #events}. */
    public JsonNode verify(byte[] body) {
        Set<String> allowed = new HashSet<>();
        for (String a : (topicArns == null ? "" : topicArns).split(",")) if (!a.isBlank()) allowed.add(a.trim());
        if (allowed.isEmpty()) throw new HandyFlowException("SES webhook is not configured.", HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_UNCONFIGURED");
        try {
            JsonNode m = om.readTree(body);
            if (!allowed.contains(m.path("TopicArn").asText(""))) throw new SecurityException("topic not allowed");
            verifySignature(m);
            if ("SubscriptionConfirmation".equals(m.path("Type").asText())) {
                URI u = URI.create(m.path("SubscribeURL").asText(""));
                if (!"https".equals(u.getScheme()) || u.getHost() == null || !SNS_HOST.matcher(u.getHost()).matches()) throw new SecurityException("bad SubscribeURL");
                http.send(HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
                log.info("SES SNS subscription confirmed for {}", m.path("TopicArn").asText());
            }
            return m;
        } catch (HandyFlowException e) { throw e;
        } catch (Exception e) { throw new HandyFlowException("Invalid webhook signature.", HttpStatus.UNAUTHORIZED, "WEBHOOK_UNAUTHORIZED"); }
    }

    public List<ProviderEvent> events(JsonNode envelope) {
        List<ProviderEvent> out = new ArrayList<>();
        if (!"Notification".equals(envelope.path("Type").asText())) return out;
        try {
            JsonNode msg = om.readTree(envelope.path("Message").asText("{}"));
            String kind = msg.path("eventType").asText(msg.path("notificationType").asText(""));
            String type; String reason = null;
            switch (kind) {
                case "Delivery" -> type = "DELIVERED";
                case "Complaint" -> type = "COMPLAINT";
                case "Bounce" -> {
                    if (!"Permanent".equalsIgnoreCase(msg.path("bounce").path("bounceType").asText())) return out;  // transient: ignore
                    type = "HARD_BOUNCE";
                    reason = msg.path("bounce").path("bouncedRecipients").path(0).path("diagnosticCode").asText(null);
                }
                default -> { return out; }
            }
            String cc = msg.path("mail").path("tags").path("cc").path(0).asText("");
            if (cc.isBlank()) return out;
            UUID ccId;
            try { ccId = UUID.fromString(cc); } catch (IllegalArgumentException e) { return out; }
            String recipient = switch (type) {
                case "DELIVERED" -> msg.path("delivery").path("recipients").path(0).asText(null);
                case "COMPLAINT" -> msg.path("complaint").path("complainedRecipients").path(0).path("emailAddress").asText(null);
                default -> msg.path("bounce").path("bouncedRecipients").path(0).path("emailAddress").asText(null);
            };
            String idSeed = msg.path("mail").path("messageId").asText("") + "|" + type + "|" + recipient;
            Instant at = parseTime(envelope.path("Timestamp").asText(null));
            out.add(new ProviderEvent("ses", sha(idSeed), type, ccId, recipient, at, trunc(reason, 300)));
        } catch (Exception e) { throw new HandyFlowException("Unreadable webhook body.", HttpStatus.BAD_REQUEST, "WEBHOOK_BAD_BODY"); }
        return out;
    }

    private void verifySignature(JsonNode m) throws Exception {
        URI cert = URI.create(m.path("SigningCertURL").asText(""));
        if (!"https".equals(cert.getScheme()) || cert.getHost() == null || !SNS_HOST.matcher(cert.getHost()).matches()
                || !cert.getPath().endsWith(".pem")) throw new SecurityException("bad cert url");
        byte[] pem = http.send(HttpRequest.newBuilder(cert).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
        X509Certificate x = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem));
        x.checkValidity();

        String type = m.path("Type").asText();
        List<String> fields = "Notification".equals(type)
                ? List.of("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type")
                : List.of("Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type");
        StringBuilder sb = new StringBuilder();
        for (String f : fields) if (m.has(f)) sb.append(f).append('\n').append(m.get(f).asText()).append('\n');

        String alg = "2".equals(m.path("SignatureVersion").asText()) ? "SHA256withRSA" : "SHA1withRSA";
        Signature s = Signature.getInstance(alg);
        s.initVerify(x.getPublicKey());
        s.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        if (!s.verify(Base64.getDecoder().decode(m.path("Signature").asText(""))))
            throw new SecurityException("bad signature");
    }

    private static Instant parseTime(String s) { try { return s == null ? Instant.now() : Instant.parse(s); } catch (Exception e) { return Instant.now(); } }
    private static String trunc(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
    private static String sha(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}