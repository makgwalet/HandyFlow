package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookIngestService;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookIngestService.Outcome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PUBLIC receiver for provider callbacks — no login, no TenantContext: the URL token selects the endpoint and the
 * HMAC signature authenticates the sender (see WebhookSigner for the exact spec). No @PreAuthorize and no
 * FeatureGuard on purpose, same reasoning as the portal auth controllers (neither a principal nor a tenant exists yet).
 * <p>
 * *** SECURITY CONFIG NEEDED *** — add /api/v1/debtcollection/webhooks/** to SecurityConfig's permitAll() list AND to
 * RateLimitFilter's LIMITS array, exactly as was done for the portal-auth controllers. Without the first, every
 * callback is rejected as unauthenticated; without the second the endpoint is unthrottled. The endpoint reads the raw
 * body bytes (the signature covers them exactly), so do not put a body-rewriting filter in front of it.
 * Responses never say why a request was refused beyond the status code.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/debtcollection/webhooks")
@RequiredArgsConstructor
public class WebhookReceiverController {

    private final WebhookIngestService ingestService;

    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String token,
            @RequestHeader(value = "X-HandyFlow-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-HandyFlow-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        Outcome o = ingestService.handle(token, timestamp, signature, body);
        if (o.httpStatus() == 404 || o.httpStatus() == 401) {
            return ResponseEntity.status(o.httpStatus()).build();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", o.message());
        res.put("accepted", o.accepted());
        res.put("suspended", o.suspended());
        res.put("ignored", o.ignored());
        res.put("duplicates", o.duplicates());
        res.put("invalid", o.invalid());
        res.put("failed", o.failed());
        return ResponseEntity.status(o.httpStatus()).body(res);
    }
}
