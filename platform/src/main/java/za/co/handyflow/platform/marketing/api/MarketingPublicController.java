package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingPublicService;
import za.co.handyflow.platform.marketing.dto.FormDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

@RestController
@RequestMapping("/api/v1/marketing/public")
@RequiredArgsConstructor
@Tag(name = "Marketing Public", description = "Signup forms, double opt-in and the preference centre (no authentication)")
public class MarketingPublicController {

    private final MarketingPublicService service;

    /** Set true only when a trusted proxy overwrites X-Forwarded-For; otherwise clients could spoof their IP. */
    @Value("${marketing.public.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @GetMapping("/forms/{slug}")
    @Operation(summary = "PUBLIC: signup form configuration")
    public ResponseEntity<ApiResponse<PublicForm>> form(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(service.form(slug)));
    }

    @PostMapping("/forms/{slug}/submit")
    @Operation(summary = "PUBLIC: request a subscription. Sends a confirmation email; nothing is opted in until it is confirmed.")
    public ResponseEntity<ApiResponse<Void>> submit(@PathVariable String slug, @Valid @RequestBody SubmitRequest req, HttpServletRequest http) {
        service.submit(slug, req, clientIp(http));
        return ResponseEntity.ok(ApiResponse.success("If this address can be subscribed, a confirmation email is on its way.", null));
    }

    @PostMapping("/confirm/{token}")
    @Operation(summary = "PUBLIC: confirm a subscription (POST so email scanners cannot trigger it)")
    public ResponseEntity<ApiResponse<ConfirmResult>> confirm(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(service.confirm(token)));
    }

    @GetMapping("/preferences/{token}")
    public ResponseEntity<ApiResponse<PrefView>> preferences(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(service.preferences(token)));
    }

    @PutMapping("/preferences/{token}")
    public ResponseEntity<ApiResponse<PrefView>> update(@PathVariable String token, @RequestBody PrefUpdate req) {
        return ResponseEntity.ok(ApiResponse.success("Preferences saved", service.updatePreferences(token, req)));
    }

    private String clientIp(HttpServletRequest r) {
        if (trustForwardedFor) {
            String xff = r.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return r.getRemoteAddr();
    }
}