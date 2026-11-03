package za.co.handyflow.platform.secureaccess.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.secureaccess.application.AccessCtx;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureShareService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

/**
 * Public (unauthenticated) page a share recipient uses. The path token is the recipient's own link; it is
 * NOT enough on its own. Order: POST otp -> POST verify (returns a session) -> GET view / GET download /
 * POST acknowledge with the session in the X-Recipient-Session header. Used by every module.
 *
 * Responses are no-store / noindex / no-referrer because the token is in the URL path.
 * DEPLOYMENT NOTE: set server.forward-headers-strategy so the real client IP is seen behind a proxy.
 */
@Validated
@RestController
@RequestMapping("/api/public/access/shares/{token}")
@RequiredArgsConstructor
@Tag(name = "Secure Share Access", description = "Public, per-recipient access to shared documents")
public class SecureShareController {

    static final String SESSION_HEADER = "X-Recipient-Session";

    private final SecureShareService access;

    @PostMapping("/otp")
    @Operation(summary = "Email a six-digit code to the address this link was issued to")
    public ResponseEntity<ApiResponse<OtpRequested>> requestOtp(@PathVariable String token, HttpServletRequest req) {
        return noStore(ApiResponse.success("A code has been sent", access.requestOtp(token, ctx(req))));
    }

    @PostMapping("/verify")
    @Operation(summary = "Exchange the emailed code for a short session")
    public ResponseEntity<ApiResponse<SessionGranted>> verify(
            @PathVariable String token, @Valid @RequestBody VerifyOtpRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Verified", access.verifyOtp(token, body.code(), ctx(req))));
    }

    @GetMapping
    @Operation(summary = "The share and only the documents assigned to this recipient")
    public ResponseEntity<ApiResponse<View>> view(
            @PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
            HttpServletRequest req) {
        return noStore(ApiResponse.success("Success", access.view(token, session, ctx(req))));
    }

    @GetMapping("/documents/{documentId}/download")
    @Operation(summary = "Download the exact revision that was issued (attachment, never rendered)")
    public ResponseEntity<byte[]> download(
            @PathVariable String token, @PathVariable UUID documentId,
            @RequestHeader(value = SESSION_HEADER, required = false) String session, HttpServletRequest req) {
        return SecureDownloads.response(access.download(token, session, documentId, ctx(req)));
    }

    @PostMapping("/acknowledge")
    @Operation(summary = "Acknowledge receipt (once)")
    public ResponseEntity<ApiResponse<View>> acknowledge(
            @PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
            @Valid @RequestBody(required = false) AcknowledgeRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Acknowledged",
                access.acknowledge(token, session, body == null ? null : body.note(), ctx(req))));
    }

    static AccessCtx ctx(HttpServletRequest req) {
        return new AccessCtx(req.getRemoteAddr(), req.getHeader("User-Agent"));
    }

    static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }
}
