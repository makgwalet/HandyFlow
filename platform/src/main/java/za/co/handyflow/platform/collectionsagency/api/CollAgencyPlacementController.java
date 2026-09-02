package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPlacementService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPlacementService.DebtorPlacementLine;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPlacementBatch;
import za.co.handyflow.platform.collectionsagency.dto.*;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The placement/handover workflow: a creditor client places a batch of
 * debtor accounts, the agency acknowledges receipt. This controller
 * covers batch creation/acknowledgment/listing; individual debtor-account
 * management moves to CollAgencyDebtorAccountController once an account
 * exists.
 */
@RestController
@RequestMapping("/api/v1/collections-agency/clients/{clientId}/placement-batches")
@RequiredArgsConstructor
@Tag(name = "Collections Agency - Placement", description = "Bulk debtor-account placement/handover")
public class CollAgencyPlacementController {

    private static final String EVIDENCE_ENTITY_TYPE = "CollAgencyPlacementBatch";

    private final CollAgencyPlacementService placementService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<PlacementBatchResponse>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                placementService.listForClient(TenantContext.getTenantIdAsObject(), clientId)
                        .stream().map(this::toResponse).toList()));
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<PlacementBatchResponse>> get(@PathVariable UUID clientId,
                                                                    @PathVariable UUID batchId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(placementService.get(TenantContext.getTenantIdAsObject(), batchId))));
    }

    @GetMapping("/{batchId}/accounts")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "List the debtor accounts created by this batch")
    public ResponseEntity<ApiResponse<List<DebtorAccountResponse>>> accountsInBatch(@PathVariable UUID clientId,
                                                                                     @PathVariable UUID batchId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                placementService.accountsInBatch(TenantContext.getTenantIdAsObject(), batchId)
                        .stream().map(CollAgencyDebtorAccountController::toResponseStatic).toList()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Place a new batch of debtor accounts with this agency (bulk import)")
    public ResponseEntity<ApiResponse<PlacementBatchResponse>> createBatch(@PathVariable UUID clientId,
                                                                            @Valid @RequestBody CreatePlacementBatchRequest req) {
        featureGuard.requireModule("collectionsagency");
        List<DebtorPlacementLine> lines = req.lines().stream()
                .map(l -> new DebtorPlacementLine(l.accountReference(), l.debtorName(), l.debtorIdNumber(),
                        l.debtorEmail(), l.debtorPhone(), l.debtorAddress(), l.originalCreditorName(),
                        l.originalDebtDate(), l.originalDebtAmount()))
                .toList();
        CollAgencyPlacementBatch batch = placementService.createBatch(TenantContext.getTenantIdAsObject(), clientId,
                req.batchReference(), req.placedDate(), lines, req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Batch placed", toResponse(batch)));
    }

    @PostMapping("/{batchId}/acknowledge")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Confirm the agency has received and begun processing this batch")
    public ResponseEntity<ApiResponse<PlacementBatchResponse>> acknowledge(@PathVariable UUID clientId,
                                                                            @PathVariable UUID batchId) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyPlacementBatch batch = placementService.acknowledge(TenantContext.getTenantIdAsObject(), batchId,
                TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Batch acknowledged", toResponse(batch)));
    }

    // ── Evidence (client mandate, original placement spreadsheet, ...) ─────────

    @PostMapping(value = "/{batchId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Attach a supporting document (client mandate, original placement file, ...) to this batch")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(@PathVariable UUID clientId,
            @PathVariable UUID batchId, @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("collectionsagency");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        placementService.get(tenantId, batchId); // 404s if the batch doesn't exist or isn't this tenant's
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "collectionsagency",
                EVIDENCE_ENTITY_TYPE, batchId, null, TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/{batchId}/evidence")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID clientId,
            @PathVariable UUID batchId) {
        featureGuard.requireModule("collectionsagency");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        placementService.get(tenantId, batchId);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, "collectionsagency", EVIDENCE_ENTITY_TYPE, batchId)));
    }

    private PlacementBatchResponse toResponse(CollAgencyPlacementBatch b) {
        return new PlacementBatchResponse(b.getId(), b.getClientId(), b.getBatchReference(), b.getPlacedDate(),
                b.getTotalAccounts(), b.getTotalPlacedValue(), b.getAcknowledgedAt(), b.getAcknowledgedBy(),
                b.getNotes());
    }
}
