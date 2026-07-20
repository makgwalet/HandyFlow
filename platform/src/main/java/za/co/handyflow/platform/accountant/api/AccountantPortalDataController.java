package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accountant.application.internal.AccountantPortalDataService;
import za.co.handyflow.platform.accountant.dto.FeeNoteResponse;
import za.co.handyflow.platform.accountant.dto.FicaDocumentResponse;
import za.co.handyflow.platform.accountant.dto.PortalClientSummaryResponse;
import za.co.handyflow.platform.accountant.dto.UploadFicaDocumentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.UUID;

/**
 * Closes the "client portal" gap's actual data layer.
 * <p>
 * Class-level @PreAuthorize("hasAuthority('PORTAL_USER')") deliberately
 * covers every method in this controller — a new endpoint added here
 * later automatically requires a portal session too, rather than
 * depending on whoever adds it remembering to annotate it individually.
 */
@RestController
@RequestMapping("/api/v1/accountant/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Accountant Client Portal", description = "Client-facing data access")
public class AccountantPortalDataController {

    private final AccountantPortalDataService portalDataService;

    @GetMapping("/me/clients")
    @Operation(summary = "Clients the logged-in portal user has active access to")
    public ResponseEntity<ApiResponse<List<PortalClientSummaryResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success("Your clients",
                portalDataService.getMyClients(getPortalUserId())));
    }

    @GetMapping("/clients/{clientId}/fee-notes")
    @Operation(summary = "Fee note history for a client the portal user has access to")
    public ResponseEntity<ApiResponse<List<FeeNoteResponse>>> getMyFeeNotes(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success("Fee notes",
                portalDataService.getMyFeeNotes(getPortalUserId(), clientId)));
    }

    @GetMapping(value = "/clients/{clientId}/fee-notes/{feeNoteId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a fee note PDF")
    public ResponseEntity<byte[]> downloadMyFeeNotePdf(
            @PathVariable UUID clientId, @PathVariable UUID feeNoteId) {
        byte[] pdf = portalDataService.downloadMyFeeNotePdf(getPortalUserId(), clientId, feeNoteId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + feeNoteId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/clients/{clientId}/fica-documents")
    @Operation(summary = "FICA documents for a client the portal user has access to")
    public ResponseEntity<ApiResponse<List<FicaDocumentResponse>>> getMyFicaDocuments(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success("FICA documents",
                portalDataService.getMyFicaDocuments(getPortalUserId(), clientId)));
    }

    @PostMapping("/clients/{clientId}/fica-documents")
    @Operation(summary = "Upload a FICA document (max 10MB — PDF, JPG, PNG, or Word)")
    public ResponseEntity<ApiResponse<FicaDocumentResponse>> uploadMyFicaDocument(
            @PathVariable UUID clientId, @Valid @RequestBody UploadFicaDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                portalDataService.uploadMyFicaDocument(getPortalUserId(), clientId, req)));
    }

    @GetMapping(value = "/clients/{clientId}/fica-documents/{docId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Download a FICA document")
    public ResponseEntity<byte[]> downloadMyFicaDocument(
            @PathVariable UUID clientId, @PathVariable UUID docId) {
        var file = portalDataService.downloadMyFicaDocument(getPortalUserId(), clientId, docId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    /** PortalJwtFilter stores the portal user's ID (UUID string) as the Authentication principal. */
    private UUID getPortalUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new HandyFlowException("No portal session", HttpStatus.UNAUTHORIZED, "NO_SESSION");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}