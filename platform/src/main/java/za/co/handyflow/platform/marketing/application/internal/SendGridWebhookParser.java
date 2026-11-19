package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SendGridWebhookParser {

    private final ObjectMapper om;

    @Value("${marketing.webhook.sendgrid-public-key:}")
    private String publicKey;

    /** Verifies the signed webhook (ECDSA over timestamp + raw body). Throws 503 if unconfigured, 401 if invalid. */
    public void verify(byte[] body, String signature, String timestamp) {
        if (publicKey == null || publicKey.isBlank()) throw new HandyFlowException("SendGrid webhook is not configured.", HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_UNCONFIGURED");
        try {
            if (signature == null || timestamp == null) throw new SecurityException("missing headers");
            long ts = Long.parseLong(timestamp.trim());
            if (Math.abs(Instant.now().getEpochSecond() - ts) > 600) throw new SecurityException("stale");
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.trim()))));
            s.update(timestamp.trim().getBytes(StandardCharsets.UTF_8));
            s.update(body);
            if (!s.verify(Base64.getDecoder().decode(signature.trim()))) throw new SecurityException("bad signature");
        } catch (HandyFlowException e) { throw e;
        } catch (Exception e) { throw new HandyFlowException("Invalid webhook signature.", HttpStatus.UNAUTHORIZED, "WEBHOOK_UNAUTHORIZED"); }
    }

    public List<ProviderEvent> parse(byte[] body) {
        List<ProviderEvent> out = new ArrayList<>();
        try {
            JsonNode arr = om.readTree(body);
            if (!arr.isArray()) return out;
            for (JsonNode n : arr) {
                String event = n.path("event").asText("");
                String type = switch (event) {
                    case "delivered" -> "DELIVERED";
                    case "bounce" -> "HARD_BOUNCE";            // SendGrid "blocked" is a temporary rejection: ignored
                    case "spamreport" -> "COMPLAINT";
                    default -> null;
                };
                String cc = n.path("cc").asText("");            // unique_args.cc is echoed as a top-level field
                String id = n.path("sg_event_id").asText("");
                if (type == null || cc.isBlank() || id.isBlank()) continue;
                UUID ccId;
                try { ccId = UUID.fromString(cc); } catch (IllegalArgumentException e) { continue; }
                out.add(new ProviderEvent("sendgrid", id, type, ccId, n.path("email").asText(null),
                        Instant.ofEpochSecond(n.path("timestamp").asLong(Instant.now().getEpochSecond())),
                        trunc(n.path("reason").asText(null), 300)));
            }
        } catch (Exception e) { throw new HandyFlowException("Unreadable webhook body.", HttpStatus.BAD_REQUEST, "WEBHOOK_BAD_BODY"); }
        return out;
    }

    private static String trunc(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}