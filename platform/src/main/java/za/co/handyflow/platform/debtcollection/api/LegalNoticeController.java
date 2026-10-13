package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.LegalNoticeService;
import za.co.handyflow.platform.debtcollection.domain.model.LegalNotice;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Statutory/formal notice tracking (section 129 and letters of demand). Recording and delivery updates are
 * MANAGE/ADMIN; the legal review is supervisor-only and must be by someone other than the person who recorded
 * the notice. Responses carry an explicitly-advisory business-day calculation for section 129 notices.
 */
@RestController
@RequestMapping("/api/v1/debtcollection")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Legal Notices", description = "Section 129 / demand notice evidence and review")
public class LegalNoticeController {

    private final LegalNoticeService service;
    private final FeatureGuard featureGuard;

    @PostMapping("/cases/{caseId}/legal-notices")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a notice; link a LETTER communication to pre-fill dispatch and delivery facts")
    public ResponseEntity<ApiResponse<NoticeResponse>> record(@PathVariable UUID caseId, @Valid @RequestBody RecordNoticeRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalNotice n = service.record(tenant(), caseId, req.noticeType(), req.noticeVersion(), req.addressUsed(),
                req.addressSource(), req.dispatchMethod(), req.dispatchedDate(), req.trackingReference(),
                req.postOfficeBranch(), req.collectionNoticeReference(), req.defaultDate(), req.communicationId(),
                req.evidenceId(), req.notes(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Notice recorded", view(n)));
    }

    @GetMapping("/cases/{caseId}/legal-notices")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<NoticeResponse>>> list(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(service.listForCase(tenant(), caseId).stream().map(this::view).toList()));
    }

    @GetMapping("/legal-notices/{id}")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<NoticeResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(view(service.get(tenant(), id))));
    }

    @PostMapping("/legal-notices/{id}/delivery")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record delivery facts (delivered / returned-uncollected / failed). Resets any earlier legal review")
    public ResponseEntity<ApiResponse<NoticeResponse>> delivery(@PathVariable UUID id, @Valid @RequestBody NoticeDeliveryRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalNotice n = service.recordDelivery(tenant(), id, req.status(), req.deliveredDate(), req.detail(),
                req.postOfficeBranch(), req.collectionNoticeReference(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Delivery recorded", view(n)));
    }

    @PostMapping("/legal-notices/{id}/sync-communication")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Refresh dispatch/delivery facts from the linked LETTER communication (resets review)")
    public ResponseEntity<ApiResponse<NoticeResponse>> sync(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        LegalNotice n = service.syncFromCommunication(tenant(), id, TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Synced from communication", view(n)));
    }

    @PostMapping("/legal-notices/{id}/review")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Legal review of the notice evidence (ACCEPTED/REJECTED) — reviewer must differ from the recorder")
    public ResponseEntity<ApiResponse<NoticeResponse>> review(@PathVariable UUID id, @Valid @RequestBody NoticeReviewRequest req) {
        featureGuard.requireModule("debtcollection");
        LegalNotice n = service.review(tenant(), id, req.outcome(), req.note(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Review recorded", view(n)));
    }

    @GetMapping("/legal-notices/{id}/history")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<LegalEventResponse>>> history(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.history(tenant(), id).stream().map(LegalEventResponse::of).toList()));
    }

    private NoticeResponse view(LegalNotice n) {
        return NoticeResponse.of(n, service.advisory(n));
    }

    private TenantId tenant() {
        return TenantContext.getTenantIdAsObject();
    }
}
