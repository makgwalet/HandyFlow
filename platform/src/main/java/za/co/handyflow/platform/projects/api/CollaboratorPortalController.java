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
import za.co.handyflow.platform.projects.application.internal.CollaboratorAccessService;
import za.co.handyflow.platform.projects.application.internal.DocumentService;
import za.co.handyflow.platform.projects.application.internal.TransmittalAccessService.Ctx;
import za.co.handyflow.platform.projects.dto.CollaboratorPortalDtos;
import za.co.handyflow.platform.projects.dto.RecipientPortalDtos;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

/**
 * Public workspace of a registered collaborator. The path token is their standing link and is NOT enough on
 * its own. Order: POST otp -> POST verify (returns a session) -> GET workspace / GET download / POST
 * acknowledge with the session in the X-Recipient-Session header. Same headers and proxy note as
 * RecipientTransmittalController.
 */
@Validated
@RestController
@RequestMapping("/api/public/projects/collaborators/{token}")
@RequiredArgsConstructor
@Tag(name = "Collaborator Workspace", description = "Public workspace for registered external collaborators")
public class CollaboratorPortalController {

    private static final String SESSION_HEADER = RecipientTransmittalController.SESSION_HEADER;

    private final CollaboratorAccessService collaborators;

    @PostMapping("/otp")
    @Operation(summary = "Email a six-digit code to the address on this account")
    public ResponseEntity<ApiResponse<RecipientPortalDtos.OtpRequested>> requestOtp(
            @PathVariable String token, HttpServletRequest req) {
        return noStore(ApiResponse.success("A code has been sent", collaborators.requestOtp(token, ctx(req))));
    }

    @PostMapping("/verify")
    @Operation(summary = "Exchange the emailed code for a short session")
    public ResponseEntity<ApiResponse<RecipientPortalDtos.SessionGranted>> verify(
            @PathVariable String token, @Valid @RequestBody RecipientPortalDtos.VerifyOtpRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Verified", collaborators.verifyOtp(token, body.code(), ctx(req))));
    }

    @GetMapping
    @Operation(summary = "The transmittals open to this collaborator, each with only the documents assigned to them")
    public ResponseEntity<ApiResponse<CollaboratorPortalDtos.Workspace>> workspace(
            @PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
            HttpServletRequest req) {
        return noStore(ApiResponse.success("Success", collaborators.workspace(token, session, ctx(req))));
    }

    @GetMapping("/transmittals/{recipientId}/documents/{documentId}/download")
    @Operation(summary = "Download the exact revision that was issued (attachment, never rendered)")
    public ResponseEntity<byte[]> download(
            @PathVariable String token, @PathVariable UUID recipientId, @PathVariable UUID documentId,
            @RequestHeader(value = SESSION_HEADER, required = false) String session, HttpServletRequest req) {
        DocumentService.FileDownload file = collaborators.download(token, session, recipientId, documentId, ctx(req));
        return DocumentDownloads.response(file);
    }

    @PostMapping("/transmittals/{recipientId}/acknowledge")
    @Operation(summary = "Acknowledge receipt of one transmittal (once)")
    public ResponseEntity<ApiResponse<CollaboratorPortalDtos.Workspace>> acknowledge(
            @PathVariable String token, @PathVariable UUID recipientId,
            @RequestHeader(value = SESSION_HEADER, required = false) String session,
            @Valid @RequestBody(required = false) RecipientPortalDtos.AcknowledgeRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Acknowledged",
                collaborators.acknowledge(token, session, recipientId, body == null ? null : body.note(), ctx(req))));
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
