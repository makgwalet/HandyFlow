package za.co.handyflow.platform.marketing.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;

/**
 * Literal paths /webhooks/sendgrid and /webhooks/ses take precedence over the generic /webhooks/{provider}
 * mapping from Chunk 3. Both are covered by your existing permitAll for /api/v1/marketing/webhooks/**.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/marketing/webhooks")
@RequiredArgsConstructor
public class MarketingProviderWebhookController {

    private final SendGridWebhookParser sendgrid;
    private final SesWebhookParser      ses;
    private final ProviderEventBridge   bridge;

    @PostMapping("/sendgrid")
    public ResponseEntity<ApiResponse<Void>> sendgrid(@RequestBody byte[] body,
                                                      @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false) String sig,
                                                      @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Timestamp", required = false) String ts) {
        sendgrid.verify(body, sig, ts);
        return apply(sendgrid.parse(body));
    }

    /** SNS posts as text/plain; the body is read raw so the signature is checked against what Amazon sent. */
    @PostMapping("/ses")
    public ResponseEntity<ApiResponse<Void>> ses(@RequestBody byte[] body) {
        var envelope = ses.verify(body);
        return apply(ses.events(envelope));
    }

    private ResponseEntity<ApiResponse<Void>> apply(List<ProviderEvent> events) {
        int failed = 0;
        for (ProviderEvent e : events) {
            try { bridge.apply(e); }
            catch (Exception ex) { failed++; log.error("Webhook event {} failed: {}", e.eventId(), ex.getMessage()); }
        }
        if (failed > 0) return ResponseEntity.status(500).body(ApiResponse.success("Some events failed; retry", null));
        return ResponseEntity.ok(ApiResponse.success("OK", null));
    }
}