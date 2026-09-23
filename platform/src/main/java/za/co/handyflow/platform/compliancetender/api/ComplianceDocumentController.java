package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.ComplianceDocumentService;
import za.co.handyflow.platform.compliancetender.dto.ComplianceDocumentResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/documents")
@RequiredArgsConstructor
@Tag(name = "Compliance - Documents", description = "The compliance document vault — thin wrapper over EvidenceFacade")
public class ComplianceDocumentController {

    private final ComplianceDocumentService documentService;
    private final FeatureGuard featureGuard;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Upload a compliance document, optionally linked to a registration")
    public ResponseEntity<ApiResponse<ComplianceDocumentResponse>> upload(
            @RequestParam(required = false) UUID registrationId,
            @RequestParam String documentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam("file") MultipartFile file) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                documentService.upload(TenantContext.getTenantIdAsObject(), registrationId, documentType,
                        issueDate, expiryDate, file, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplianceDocumentResponse>>> getDocuments(
            @RequestParam(required = false) UUID registrationId) {
        featureGuard.requireModule("compliancetender");
        var tenantId = TenantContext.getTenantIdAsObject();
        var documents = registrationId != null
                ? documentService.getDocumentsForRegistration(tenantId, registrationId)
                : documentService.getAllDocuments(tenantId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Mark a document as human-reviewed and verified")
    public ResponseEntity<ApiResponse<ComplianceDocumentResponse>> verify(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success("Document verified",
                documentService.verify(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        documentService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }
}
