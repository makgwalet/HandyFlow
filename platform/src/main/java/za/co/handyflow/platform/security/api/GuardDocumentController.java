// security/api/GuardDocumentController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GuardService;
import za.co.handyflow.platform.security.dto.DeleteGuardDocumentRequest;
import za.co.handyflow.platform.security.dto.GuardDocumentResponse;
import za.co.handyflow.platform.security.dto.UploadGuardDocumentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Guard file documents (ID copy, PSiRA certificate, proof of address...).
 * GuardService already implemented upload, list and delete, but nothing
 * exposed them over HTTP; this controller does. Reads need SECURITY_READ,
 * changes SECURITY_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/security/guards/{guardId}/documents")
@RequiredArgsConstructor
@Tag(name = "Security - Guard documents", description = "Compliance documents on a guard's file")
public class GuardDocumentController {

    private final GuardService guardService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List a guard's documents")
    public ResponseEntity<ApiResponse<List<GuardDocumentResponse>>> list(@PathVariable UUID guardId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                guardService.getDocuments(TenantContext.getTenantIdAsObject(), guardId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a document to a guard's file")
    public ResponseEntity<ApiResponse<GuardDocumentResponse>> upload(
            @PathVariable UUID guardId, @Valid @RequestBody UploadGuardDocumentRequest request) {
        featureGuard.requireModule("security");
        var doc = guardService.uploadDocument(TenantContext.getTenantIdAsObject(), guardId,
                request, TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document added", doc));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove a document (soft delete, a reason is required)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID guardId, @PathVariable UUID documentId,
            @Valid @RequestBody DeleteGuardDocumentRequest request) {
        featureGuard.requireModule("security");
        guardService.deleteDocument(TenantContext.getTenantIdAsObject(), documentId,
                TenantContext.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Document removed", null));
    }
}
