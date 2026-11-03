package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.DocumentService;
import za.co.handyflow.platform.projects.application.internal.TransmittalAccessService;
import za.co.handyflow.platform.projects.application.internal.TransmittalAccessService.Ctx;
import za.co.handyflow.platform.projects.dto.RecipientPortalDtos;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

/**
 * Public (unauthenticated) page a transmittal recipient uses. The path token is the recipient's own
 * link; it is NOT enough on its own. Order of use: POST otp → POST verify (returns a session) → GET view
 * / GET download / POST acknowledge with the session in the X-Recipient-Session header.
 *
 * Responses are marked no-store / noindex / no-referrer because the token is in the URL path.
 * DEPLOYMENT NOTE: as for the client portal, set server.forward-headers-strategy so the real client IP
 * (used for rate limiting and written to the access log) is seen behind a proxy.
 */
@Validated
@RestController
@RequestMapping("/api/public/projects/transmittals/{token}")
@RequiredArgsConstructor
@Tag(name = "Transmittal Recipient Access", description = "Public, per-recipient access to transmitted documents")
public class RecipientTransmittalController {

    static final String SESSION_HEADER = "X-Recipient-Session";

    private final TransmittalAccessService access;

    @PostMapping("/otp")
    @Operation(summary = "Email a six-digit code to the address this link was issued to")
    public ResponseEntity<ApiResponse<RecipientPortalDtos.OtpRequested>> requestOtp(
            @PathVariable String token, HttpServletRequest req) {
        return noStore(ApiResponse.success("A code has been sent", access.requestOtp(token, ctx(req))));
    }

    @PostMapping("/verify")
    @Operation(summary = "Exchange the emailed code for a short session")
    public ResponseEntity<ApiResponse<RecipientPortalDtos.SessionGranted>> verify(
            @PathVariable String token, @Valid @RequestBody RecipientPortalDtos.VerifyOtpRequest body,
            HttpServletRequest req) {
        return noStore(ApiResponse.success("Verified", access.verifyOtp(token, body.code(), ctx(req))));
    }

    @GetMapping
    @Operation(summary = "The transmittal and only the documents assigned to this recipient")
    public ResponseEntity<ApiResponse<RecipientPortalDtos.View>> view(
            @PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
            HttpServletRequest req) {
        return noStore(ApiResponse.success("Success", access.view(token, session, ctx(req))));
    }

    @GetMapping("/documents/{documentId}/download")
    @Operation(summary = "Download the exact revision that was issued (attachment, never rendered)")
    public ResponseEntity<byte[]> download(
            @PathVariable String token, @PathVariable UUID documentId,
            @RequestHeader(value = SESSION_HEADER, required = false) String session, HttpServletRequest req) {
        DocumentService.FileDownload file = access.download(token, session, documentId, ctx(req));
        return DocumentDownloads.response(file);
    }

    @PostMapping("/acknowledge")
    @Operation(summary = "Acknowledge receipt (once)")
    public ResponseEntity<ApiResponse<RecipientPortalDtos.View>> acknowledge(
            @PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
            @Valid @RequestBody(required = false) RecipientPortalDtos.AcknowledgeRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Acknowledged",
                access.acknowledge(token, session, body == null ? null : body.note(), ctx(req))));
    }

    private static Ctx ctx(HttpServletRequest req) {
        return new Ctx(req.getRemoteAddr(), req.getHeader("User-Agent"));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }
}
