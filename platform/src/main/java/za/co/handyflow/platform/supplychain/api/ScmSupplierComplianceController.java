package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceService;
import za.co.handyflow.platform.supplychain.application.internal.SupplierDocumentService;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScorecardService;
import za.co.handyflow.platform.supplychain.dto.SupplierComplianceResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierDocumentResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierScorecardResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Supplier compliance documents, compliance status and scorecards. */
@RestController
@RequestMapping("/api/v1/supply-chain/suppliers")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supplier compliance and performance")
public class ScmSupplierComplianceController {

    private final FeatureGuard featureGuard;
    private final SupplierDocumentService documents;
    private final SupplierComplianceService compliance;
    private final SupplierScorecardService scorecards;

    // ── Documents ─────────────────────────────────────────────────────────────


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_MANAGE','SCM_ADMIN')")
    @Operation(summary = "Add a compliance document (file optional). Supersedes the current document of the same type.")
    public ResponseEntity<ApiResponse<SupplierDocumentResponse>> addDocument(
            @PathVariable UUID id,
            @RequestParam String docType,
            @RequestParam(required = false) String referenceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document added",
                documents.add(tenant(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(),
                        id, docType, referenceNumber, issuedDate, expiryDate, file)));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('SCM_READ')")
    public ResponseEntity<ApiResponse<List<SupplierDocumentResponse>>> listDocuments(
            @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean history) {
        return ResponseEntity.ok(ApiResponse.success("Success", documents.list(tenant(), id, history)));
    }

    @PostMapping("/{id}/documents/{docId}/verify")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_APPROVE','SCM_ADMIN')")
    @Operation(summary = "Mark a document as verified (checked as genuine)")
    public ResponseEntity<ApiResponse<SupplierDocumentResponse>> verifyDocument(
            @PathVariable UUID id, @PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.success("Document verified",
                documents.verify(tenant(), id, docId, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @DeleteMapping("/{id}/documents/{docId}")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_MANAGE','SCM_ADMIN')")
    @Operation(summary = "Remove a document (soft — kept for audit; reason required)")
    public ResponseEntity<ApiResponse<Void>> removeDocument(
            @PathVariable UUID id, @PathVariable UUID docId, @RequestParam String reason) {
        documents.remove(tenant(), id, docId, reason, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Document removed", null));
    }

    @GetMapping("/{id}/documents/{docId}/download")
    @PreAuthorize("hasAuthority('SCM_READ')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id, @PathVariable UUID docId) {
        EvidenceFacade.DownloadedEvidence f = documents.download(tenant(), id, docId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.fileName() + "\"")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.content());
    }

    // ── Compliance & scorecards ───────────────────────────────────────────────

    @GetMapping("/{id}/compliance")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Compliance status against the tenant's required-document policy")
    public ResponseEntity<ApiResponse<SupplierComplianceResponse>> getCompliance(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", compliance.describe(tenant(), id)));
    }

    @GetMapping("/{id}/scorecard")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Supplier scorecard over a rolling window (default 12 months)")
    public ResponseEntity<ApiResponse<SupplierScorecardResponse>> scorecard(
            @PathVariable UUID id, @RequestParam(required = false) Integer months) {
        return ResponseEntity.ok(ApiResponse.success("Success", scorecards.one(tenant(), id, months)));
    }

    @GetMapping("/scorecards")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Scorecards for every supplier, best first")
    public ResponseEntity<ApiResponse<List<SupplierScorecardResponse>>> allScorecards(
            @RequestParam(required = false) Integer months) {
        return ResponseEntity.ok(ApiResponse.success("Success", scorecards.all(tenant(), months)));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
