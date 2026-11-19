package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingWebhookService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PUBLIC endpoint (no JWT) called by the email provider. SecurityConfig must have
 * /api/v1/marketing/webhooks/** in permitAll(). Authentication is a shared secret
 * (X-Webhook-Secret header) or, for Mailgun, the signature inside the payload.
 *
 * Returns 500 if any event failed to apply so the provider retries; events are
 * de-duplicated, so a retry never double-counts.
 */
@RestController
@RequestMapping("/api/v1/marketing/webhooks")
@RequiredArgsConstructor
@Tag(name = "Marketing Webhooks", description = "Provider delivery callbacks (public, secret-protected)")
public class MarketingWebhookController {

    private final MarketingWebhookService webhookService;

    @PostMapping("/{provider}")
    @Operation(summary = "PUBLIC — receive delivery events from an email provider (generic | postmark | mailgun)")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String provider,
            @RequestBody String body,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {

        var r = webhookService.handle(provider, body, secret);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("received", r.received());
        out.put("applied", r.applied());
        out.put("duplicates", r.duplicates());
        out.put("unmatched", r.unmatched());
        out.put("ignored", r.ignored());
        out.put("failed", r.failed());
        return ResponseEntity.status(r.failed() > 0 ? 500 : 200).body(out);
    }
}