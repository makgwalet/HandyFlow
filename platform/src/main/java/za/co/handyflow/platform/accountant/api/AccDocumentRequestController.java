package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accountant.application.internal.AccDocumentRequestService;
import za.co.handyflow.platform.accountant.dto.CreateDocumentRequestRequest;
import za.co.handyflow.platform.accountant.dto.DocumentRequestResponse;
import za.co.handyflow.platform.accountant.dto.UpdateDocumentRequestStatusRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Closes the accountant module audit's "document requests" gap.
 */
@RestController
@RequestMapping("/api/v1/accountant")
@RequiredArgsConstructor
@Tag(name = "Accountant Document Requests", description = "Requesting documents/information from clients")
public class AccDocumentRequestController {

    private final AccDocumentRequestService requestService;

    @PostMapping("/clients/{id}/document-requests")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Request documents/information from a client")
    public ResponseEntity<ApiResponse<DocumentRequestResponse>> createRequest(
            @PathVariable UUID id, @Valid @RequestBody CreateDocumentRequestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document request created",
                requestService.createRequest(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/document-requests")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List document requests for a client")
    public ResponseEntity<ApiResponse<List<DocumentRequestResponse>>> getRequests(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Document requests",
                requestService.getRequests(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/document-requests/{requestId}/status")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Update a document request's status — PENDING, PARTIAL, COMPLETE, or CANCELLED")
    public ResponseEntity<ApiResponse<DocumentRequestResponse>> updateStatus(
            @PathVariable UUID id, @PathVariable UUID requestId,
            @Valid @RequestBody UpdateDocumentRequestStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                requestService.updateStatus(TenantContext.getTenantIdAsObject(), id, requestId, req)));
    }
}