package za.co.handyflow.platform.agriculture.api;

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
import za.co.handyflow.platform.agriculture.application.internal.AgBreedingRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgFeedRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgGroupService;
import za.co.handyflow.platform.agriculture.application.internal.AgHealthEventService;
import za.co.handyflow.platform.agriculture.application.internal.AgMortalityRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgMovementRecordService;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Batch/flock/herd-tracked groups plus their sub-resources — mirrors
 * AgAnimalController. Health-event and breeding-record TRANSITION endpoints
 * (complete/acknowledge/confirm-pregnant/etc.) are NOT duplicated here —
 * see AgAnimalController's own class Javadoc for why a single, record-id-
 * addressed set of endpoints there covers both animal- and group-linked
 * records.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Groups", description = "Batch/flock/herd-tracked livestock and their full history")
public class AgGroupController {

    private final AgGroupService groupService;
    private final AgHealthEventService healthEventService;
    private final AgBreedingRecordService breedingRecordService;
    private final AgMovementRecordService movementRecordService;
    private final AgMortalityRecordService mortalityRecordService;
    private final AgFeedRecordService feedRecordService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    private static final String SOURCE_MODULE = "agriculture";
    private static final String ENTITY_TYPE = "AgGroup";

    // ── Groups ───────────────────────────────────────────────────────────

    @GetMapping("/farms/{farmId}/groups")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<GroupResponse>>> getGroupsForFarm(
            @PathVariable UUID farmId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                groupService.getGroupsForFarm(TenantContext.getTenantIdAsObject(), farmId, status, pageable)));
    }

    @PostMapping("/farms/{farmId}/groups")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Register a new batch/flock/herd group")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @PathVariable UUID farmId, @Valid @RequestBody CreateGroupRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Group registered",
                groupService.createGroup(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                groupService.getGroup(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(
            @PathVariable UUID id, @Valid @RequestBody UpdateGroupRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Group updated",
                groupService.updateGroup(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/groups/{id}/move")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<GroupResponse>> moveGroup(
            @PathVariable UUID id, @RequestBody MoveGroupRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Group moved",
                groupService.moveGroup(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/groups/{id}/reduce-count")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Reduce the group's current count (e.g. a sale not routed through mortality) — auto-closes at zero")
    public ResponseEntity<ApiResponse<GroupResponse>> reduceCount(
            @PathVariable UUID id, @Valid @RequestBody AdjustGroupCountRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Count reduced",
                groupService.reduceCount(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/groups/{id}/increase-count")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<GroupResponse>> increaseCount(
            @PathVariable UUID id, @Valid @RequestBody AdjustGroupCountRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Count increased",
                groupService.increaseCount(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/groups/{id}/close")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<GroupResponse>> closeGroup(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Group closed",
                groupService.closeGroup(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/groups/{id}/reopen")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<GroupResponse>> reopenGroup(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Group reopened",
                groupService.reopenGroup(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        groupService.deleteGroup(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Group deleted", null));
    }

    // ── Weight history ───────────────────────────────────────────────────

    @GetMapping("/groups/{id}/weight-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<WeightRecordResponse>>> getWeightHistory(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                groupService.getWeightHistory(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/groups/{id}/weight-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record an average/sampled weight — also updates the group's current average weight")
    public ResponseEntity<ApiResponse<WeightRecordResponse>> recordAverageWeight(
            @PathVariable UUID id, @Valid @RequestBody RecordWeightRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Average weight recorded",
                groupService.recordAverageWeight(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Health events (list/create — see class Javadoc for transitions) ──

    @GetMapping("/groups/{id}/health-events")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<HealthEventResponse>>> getHealthEventsForGroup(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                healthEventService.getHistoryForGroup(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/groups/{id}/health-events")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<HealthEventResponse>> createHealthEventForGroup(
            @PathVariable UUID id, @Valid @RequestBody CreateHealthEventRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Health event recorded",
                healthEventService.createHealthEvent(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Breeding records (list/create) ──────────────────────────────────

    @GetMapping("/groups/{id}/breeding-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<BreedingRecordResponse>>> getBreedingRecordsForGroup(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                breedingRecordService.getHistoryForGroup(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/groups/{id}/breeding-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> createBreedingRecordForGroup(
            @PathVariable UUID id, @Valid @RequestBody CreateBreedingRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Breeding record created",
                breedingRecordService.createBreedingRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Movement history ─────────────────────────────────────────────────

    @GetMapping("/groups/{id}/movement-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<MovementRecordResponse>>> getMovementRecordsForGroup(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                movementRecordService.getHistoryForGroup(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/groups/{id}/movement-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<MovementRecordResponse>> createMovementRecordForGroup(
            @PathVariable UUID id, @Valid @RequestBody CreateMovementRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Movement recorded",
                movementRecordService.createMovementRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Mortality ────────────────────────────────────────────────────────

    @GetMapping("/groups/{id}/mortality-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<MortalityRecordResponse>>> getMortalityRecordsForGroup(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                mortalityRecordService.getHistoryForGroup(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/groups/{id}/mortality-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record a loss — also reduces the group's current count")
    public ResponseEntity<ApiResponse<MortalityRecordResponse>> createMortalityRecordForGroup(
            @PathVariable UUID id, @Valid @RequestBody CreateMortalityRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Mortality recorded",
                mortalityRecordService.createMortalityRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Feed history ─────────────────────────────────────────────────────

    @GetMapping("/groups/{id}/feed-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<FeedRecordResponse>>> getFeedRecordsForGroup(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                feedRecordService.getHistoryForGroup(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/groups/{id}/feed-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<FeedRecordResponse>> createFeedRecordForGroup(
            @PathVariable UUID id, @Valid @RequestBody CreateFeedRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Feed recorded",
                feedRecordService.createFeedRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Evidence ─────────────────────────────────────────────────────────

    @PostMapping(value = "/groups/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String evidenceType) {
        featureGuard.requireModule(SOURCE_MODULE);
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID uploadedBy = TenantContext.getCurrentUserId();
        String uploadedByName = TenantContext.getCurrentUserName();
        return ResponseEntity.ok(ApiResponse.success(evidenceFacade.attach(
                tenantId, file, evidenceType, SOURCE_MODULE, ENTITY_TYPE, id, null, uploadedBy, uploadedByName)));
    }

    @GetMapping("/groups/{id}/evidence")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/groups/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule(SOURCE_MODULE);
        EvidenceFacade.DownloadedEvidence file = evidenceFacade.download(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/groups/evidence/{evidenceId}/detach")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> detachEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule(SOURCE_MODULE);
        evidenceFacade.detach(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok(ApiResponse.success("Evidence detached", null));
    }
}
