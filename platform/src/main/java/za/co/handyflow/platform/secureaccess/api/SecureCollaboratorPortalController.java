package za.co.handyflow.platform.secureaccess.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureCollaboratorService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

import static za.co.handyflow.platform.secureaccess.api.SecureShareController.*;

/**
 * Public workspace for a registered collaborator: the standing link is the credential but never enough
 * alone (emailed code -> session in X-Recipient-Session). Every document request is re-checked with the
 * owning module, so a collaborator link cannot reach anything the module would no longer release.
 */
@Validated
@RestController
@RequestMapping("/api/public/access/collaborators/{token}")
@RequiredArgsConstructor
@Tag(name = "Secure Collaborator Workspace", description = "Public workspace for registered external collaborators")
public class SecureCollaboratorPortalController {

    private final SecureCollaboratorService collaborators;

    @PostMapping("/otp")
    @Operation(summary = "Email a six-digit code to the collaborator's address")
    public ResponseEntity<ApiResponse<OtpRequested>> requestOtp(@PathVariable String token, HttpServletRequest req) {
        return noStore(ApiResponse.success("A code has been sent", collaborators.requestOtp(token, ctx(req))));
    }

    @PostMapping("/verify")
    @Operation(summary = "Exchange the emailed code for a short session")
    public ResponseEntity<ApiResponse<SessionGranted>> verify(
            @PathVariable String token, @Valid @RequestBody VerifyOtpRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Verified", collaborators.verifyOtp(token, body.code(), ctx(req))));
    }

    @GetMapping
    @Operation(summary = "Every share still open to this collaborator, with only the documents assigned to them")
    public ResponseEntity<ApiResponse<Workspace>> workspace(
            @PathVariable String token, @RequestHeader(value = SESSION_HEADER, required = false) String session,
            HttpServletRequest req) {
        return noStore(ApiResponse.success("Success", collaborators.workspace(token, session, ctx(req))));
    }

    @GetMapping("/shares/{recipientId}/documents/{documentId}/download")
    @Operation(summary = "Download an issued revision (attachment, never rendered)")
    public ResponseEntity<byte[]> download(
            @PathVariable String token, @PathVariable UUID recipientId, @PathVariable UUID documentId,
            @RequestHeader(value = SESSION_HEADER, required = false) String session, HttpServletRequest req) {
        return SecureDownloads.response(collaborators.download(token, session, recipientId, documentId, ctx(req)));
    }

    @PostMapping("/shares/{recipientId}/acknowledge")
    @Operation(summary = "Acknowledge receipt of one share (once)")
    public ResponseEntity<ApiResponse<Workspace>> acknowledge(
            @PathVariable String token, @PathVariable UUID recipientId,
            @RequestHeader(value = SESSION_HEADER, required = false) String session,
            @Valid @RequestBody(required = false) AcknowledgeRequest body, HttpServletRequest req) {
        return noStore(ApiResponse.success("Acknowledged",
                collaborators.acknowledge(token, session, recipientId, body == null ? null : body.note(), ctx(req))));
    }
}
