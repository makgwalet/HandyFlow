package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalpractice.application.internal.LpDisbursementService;
import za.co.handyflow.platform.legalpractice.application.internal.LpMatterKeyDateService;
import za.co.handyflow.platform.legalpractice.application.internal.LpMatterService;
import za.co.handyflow.platform.legalpractice.application.internal.LpTimeEntryService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * The central tracking unit's own controller — matter CRUD, the full
 * lifecycle state machine, and every matter-level sub-resource (time
 * entries, disbursements, key dates, documents), mirroring how
 * {@code AgAnimalController} nests its own history sub-resources rather
 * than standing up five more top-level controllers.
 */
@RestController
@RequestMapping("/api/v1/legal-practice/matters")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Matters", description = "Matters, time entries, disbursements, key dates, documents")
public class LpMatterController {

    private static final String SOURCE_MODULE = "legalpractice";
    private static final String ENTITY_TYPE = "LpMatter";

    private final LpMatterService matterService;
    private final LpTimeEntryService timeEntryService;
    private final LpDisbursementService disbursementService;
    private final LpMatterKeyDateService keyDateService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    // ── Matter CRUD + lifecycle ───────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpMatterResponse>>> getMatters(
            @RequestParam(required = false) UUID clientId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        var tenantId = TenantContext.getTenantIdAsObject();
        Page<LpMatterResponse> page = clientId != null
                ? matterService.listForClient(tenantId, clientId, pageable)
                : matterService.listForFirm(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> getMatter(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                matterService.getMatter(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> createMatter(@Valid @RequestBody CreateLpMatterRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Matter created",
                matterService.createMatter(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> updateMatter(
            @PathVariable UUID id, @Valid @RequestBody UpdateLpMatterRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Matter updated",
                matterService.updateMatter(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> putOnHold(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Matter put on hold",
                matterService.putOnHold(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> reopen(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Matter reopened",
                matterService.reopen(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> close(
            @PathVariable UUID id, @RequestBody(required = false) CloseMatterRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Matter closed",
                matterService.close(TenantContext.getTenantIdAsObject(), id,
                        req != null ? req : new CloseMatterRequest(null))));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterResponse>> archive(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Matter archived",
                matterService.archive(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Time entries sub-resource ────────────────────────────────────────────

    @GetMapping("/{id}/time-entries")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpTimeEntryResponse>>> getTimeEntries(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                timeEntryService.listForMatter(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/{id}/time-entries/unbilled")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<LpTimeEntryResponse>>> getUnbilledTimeEntries(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                timeEntryService.listUnbilledForMatter(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/time-entries")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpTimeEntryResponse>> logTime(
            @PathVariable UUID id, @Valid @RequestBody CreateLpTimeEntryRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Time logged",
                timeEntryService.createTimeEntry(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/time-entries/{entryId}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpTimeEntryResponse>> updateTimeEntry(
            @PathVariable UUID id, @PathVariable UUID entryId, @Valid @RequestBody UpdateLpTimeEntryRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Time entry updated",
                timeEntryService.updateTimeEntry(TenantContext.getTenantIdAsObject(), entryId, req)));
    }

    @PostMapping("/{id}/time-entries/{entryId}/write-off")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpTimeEntryResponse>> writeOffTimeEntry(
            @PathVariable UUID id, @PathVariable UUID entryId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Time entry written off",
                timeEntryService.writeOff(TenantContext.getTenantIdAsObject(), entryId)));
    }

    // ── Disbursements sub-resource ───────────────────────────────────────────

    @GetMapping("/{id}/disbursements")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpDisbursementResponse>>> getDisbursements(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                disbursementService.listForMatter(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/{id}/disbursements/unbilled")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<LpDisbursementResponse>>> getUnbilledDisbursements(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                disbursementService.listUnbilledForMatter(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/disbursements")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpDisbursementResponse>> logDisbursement(
            @PathVariable UUID id, @Valid @RequestBody CreateLpDisbursementRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Disbursement logged",
                disbursementService.createDisbursement(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/disbursements/{disbursementId}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpDisbursementResponse>> updateDisbursement(
            @PathVariable UUID id, @PathVariable UUID disbursementId, @Valid @RequestBody UpdateLpDisbursementRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Disbursement updated",
                disbursementService.updateDisbursement(TenantContext.getTenantIdAsObject(), disbursementId, req)));
    }

    @PostMapping("/{id}/disbursements/{disbursementId}/write-off")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpDisbursementResponse>> writeOffDisbursement(
            @PathVariable UUID id, @PathVariable UUID disbursementId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Disbursement written off",
                disbursementService.writeOff(TenantContext.getTenantIdAsObject(), disbursementId)));
    }

    // ── Key dates sub-resource ────────────────────────────────────────────────

    @GetMapping("/{id}/key-dates")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<LpMatterKeyDateResponse>>> getKeyDates(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                keyDateService.listForMatter(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/key-dates")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterKeyDateResponse>> createKeyDate(
            @PathVariable UUID id, @Valid @RequestBody CreateLpMatterKeyDateRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Key date created",
                keyDateService.createKeyDate(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/key-dates/{keyDateId}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterKeyDateResponse>> updateKeyDate(
            @PathVariable UUID id, @PathVariable UUID keyDateId, @Valid @RequestBody UpdateLpMatterKeyDateRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Key date updated",
                keyDateService.updateKeyDate(TenantContext.getTenantIdAsObject(), keyDateId, req)));
    }

    @PostMapping("/{id}/key-dates/{keyDateId}/complete")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterKeyDateResponse>> completeKeyDate(
            @PathVariable UUID id, @PathVariable UUID keyDateId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Key date completed",
                keyDateService.complete(TenantContext.getTenantIdAsObject(), keyDateId)));
    }

    @PostMapping("/{id}/key-dates/{keyDateId}/mark-missed")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterKeyDateResponse>> markKeyDateMissed(
            @PathVariable UUID id, @PathVariable UUID keyDateId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Key date marked missed",
                keyDateService.markMissed(TenantContext.getTenantIdAsObject(), keyDateId)));
    }

    @PostMapping("/{id}/key-dates/{keyDateId}/acknowledge")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpMatterKeyDateResponse>> acknowledgeKeyDate(
            @PathVariable UUID id, @PathVariable UUID keyDateId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Key date acknowledged",
                keyDateService.acknowledge(TenantContext.getTenantIdAsObject(), keyDateId)));
    }

    // ── Matter documents (EvidenceFacade passthrough) ────────────────────────

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Attach a matter document — correspondence, court filing, signed brief, etc.")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(evidenceFacade.attach(
                TenantContext.getTenantIdAsObject(), file, evidenceType, SOURCE_MODULE, ENTITY_TYPE, id,
                null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("legalpractice");
        EvidenceFacade.DownloadedEvidence file = evidenceFacade.download(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/{id}/evidence/{evidenceId}/detach")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> detachEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("legalpractice");
        evidenceFacade.detach(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
