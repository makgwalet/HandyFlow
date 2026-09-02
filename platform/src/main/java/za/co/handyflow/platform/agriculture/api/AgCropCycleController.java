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
import za.co.handyflow.platform.agriculture.application.internal.AgCropCycleService;
import za.co.handyflow.platform.agriculture.application.internal.AgHarvestRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgInputApplicationService;
import za.co.handyflow.platform.agriculture.application.internal.AgScoutingRecordService;
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
 * The Crops sub-domain's centerpiece — {@code AgCropCycle} CRUD, its
 * planting/growing/harvest state-machine transitions, plus every
 * sub-resource that hangs off one (input applications, scouting records,
 * harvest records, evidence photos) — consolidated onto one controller the
 * same way {@code AgAnimalController} nests weight/health/breeding/
 * movement/mortality/feed history under the animal rather than giving each
 * its own top-level controller. See this module's own package-info.java
 * and {@code AgCropCycle}'s Javadoc for why the cycle is the single
 * tracking unit here — there is no individual-vs-group duality to
 * accommodate the way {@code AgAnimalController}/{@code AgGroupController}
 * must.
 * <p>
 * The scouting-record TRANSITION endpoints (resolve/reopen/
 * acknowledge-follow-up) are addressed by the record's own id alone,
 * mirroring how {@code AgAnimalController} addresses health-event
 * transitions by record id rather than by the owning animal/group.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Crop Cycles", description = "Plantings and their full history: inputs, scouting, harvest")
public class AgCropCycleController {

    private final AgCropCycleService cropCycleService;
    private final AgInputApplicationService inputApplicationService;
    private final AgScoutingRecordService scoutingRecordService;
    private final AgHarvestRecordService harvestRecordService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    private static final String SOURCE_MODULE = "agriculture";
    private static final String ENTITY_TYPE = "AgCropCycle";

    // ── Crop cycles ──────────────────────────────────────────────────────

    @GetMapping("/farms/{farmId}/crop-cycles")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<CropCycleResponse>>> getCropCyclesForFarm(
            @PathVariable UUID farmId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                cropCycleService.getCropCyclesForFarm(TenantContext.getTenantIdAsObject(), farmId, status, pageable)));
    }

    @GetMapping("/seasons/{seasonId}/crop-cycles")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<CropCycleResponse>>> getCropCyclesForSeason(
            @PathVariable UUID seasonId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                cropCycleService.getCropCyclesForSeason(TenantContext.getTenantIdAsObject(), seasonId, pageable)));
    }

    @PostMapping("/farms/{farmId}/crop-cycles")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Start a new crop cycle (planting instance)")
    public ResponseEntity<ApiResponse<CropCycleResponse>> createCropCycle(
            @PathVariable UUID farmId, @Valid @RequestBody CreateCropCycleRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Crop cycle created",
                cropCycleService.createCropCycle(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/crop-cycles/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> getCropCycle(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                cropCycleService.getCropCycle(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/crop-cycles/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> updateCropCycle(
            @PathVariable UUID id, @Valid @RequestBody UpdateCropCycleRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Crop cycle updated",
                cropCycleService.updateCropCycle(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/crop-cycles/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCropCycle(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        cropCycleService.deleteCropCycle(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Crop cycle deleted", null));
    }

    // ── Crop cycle state-machine transitions ────────────────────────────

    @PatchMapping("/crop-cycles/{id}/record-planting")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record planting — PLANNED to PLANTED — optionally issuing seed from inventory")
    public ResponseEntity<ApiResponse<CropCycleResponse>> recordPlanting(
            @PathVariable UUID id, @Valid @RequestBody RecordPlantingRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Planting recorded",
                cropCycleService.recordPlanting(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/crop-cycles/{id}/mark-growing")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> markGrowing(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Marked growing",
                cropCycleService.markGrowing(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/crop-cycles/{id}/start-harvest")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> startHarvest(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Harvest started",
                cropCycleService.startHarvest(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/crop-cycles/{id}/complete-harvest")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> completeHarvest(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Harvest completed",
                cropCycleService.completeHarvest(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/crop-cycles/{id}/mark-failed")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> markFailed(
            @PathVariable UUID id, @RequestBody(required = false) CropCycleReasonRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(ApiResponse.success("Crop cycle marked failed",
                cropCycleService.markFailed(TenantContext.getTenantIdAsObject(), id, reason)));
    }

    @PatchMapping("/crop-cycles/{id}/abandon")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropCycleResponse>> abandon(
            @PathVariable UUID id, @RequestBody(required = false) CropCycleReasonRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(ApiResponse.success("Crop cycle abandoned",
                cropCycleService.abandon(TenantContext.getTenantIdAsObject(), id, reason)));
    }

    // ── Input applications ───────────────────────────────────────────────

    @GetMapping("/crop-cycles/{id}/input-applications")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<InputApplicationResponse>>> getInputApplications(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                inputApplicationService.getHistoryForCropCycle(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/crop-cycles/{id}/input-applications")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record a fertiliser/pesticide/herbicide/fungicide/irrigation application")
    public ResponseEntity<ApiResponse<InputApplicationResponse>> createInputApplication(
            @PathVariable UUID id, @Valid @RequestBody CreateInputApplicationRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Input application recorded",
                inputApplicationService.createInputApplication(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Scouting records ─────────────────────────────────────────────────

    @GetMapping("/crop-cycles/{id}/scouting-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<ScoutingRecordResponse>>> getScoutingRecords(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                scoutingRecordService.getHistoryForCropCycle(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/crop-cycles/{id}/scouting-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<ScoutingRecordResponse>> createScoutingRecord(
            @PathVariable UUID id, @Valid @RequestBody CreateScoutingRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Scouting record created",
                scoutingRecordService.createScoutingRecord(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @GetMapping("/scouting-records/{recordId}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<ScoutingRecordResponse>> getScoutingRecord(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                scoutingRecordService.getScoutingRecord(TenantContext.getTenantIdAsObject(), recordId)));
    }

    @PutMapping("/scouting-records/{recordId}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<ScoutingRecordResponse>> updateScoutingRecord(
            @PathVariable UUID recordId, @Valid @RequestBody UpdateScoutingRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Scouting record updated",
                scoutingRecordService.updateScoutingRecord(TenantContext.getTenantIdAsObject(), recordId, request)));
    }

    @PatchMapping("/scouting-records/{recordId}/resolve")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<ScoutingRecordResponse>> resolveScoutingRecord(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Scouting record resolved",
                scoutingRecordService.resolveScoutingRecord(TenantContext.getTenantIdAsObject(), recordId)));
    }

    @PatchMapping("/scouting-records/{recordId}/reopen")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<ScoutingRecordResponse>> reopenScoutingRecord(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Scouting record reopened",
                scoutingRecordService.reopenScoutingRecord(TenantContext.getTenantIdAsObject(), recordId)));
    }

    @PatchMapping("/scouting-records/{recordId}/acknowledge-follow-up")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Acknowledge a follow-up reminder without resolving the underlying finding")
    public ResponseEntity<ApiResponse<ScoutingRecordResponse>> acknowledgeFollowUp(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Follow-up acknowledged",
                scoutingRecordService.acknowledgeFollowUp(TenantContext.getTenantIdAsObject(), recordId)));
    }

    // ── Harvest records ──────────────────────────────────────────────────

    @GetMapping("/crop-cycles/{id}/harvest-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<HarvestRecordResponse>>> getHarvestRecords(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                harvestRecordService.getHistoryForCropCycle(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/crop-cycles/{id}/harvest-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record a yield/harvest — multiple records per cycle are expected for multi-pick crops")
    public ResponseEntity<ApiResponse<HarvestRecordResponse>> createHarvestRecord(
            @PathVariable UUID id, @Valid @RequestBody CreateHarvestRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Harvest recorded",
                harvestRecordService.createHarvestRecord(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Evidence (scouting/harvest photos) ──────────────────────────────
    // Generic passthrough to EvidenceFacade, mirroring AgAnimalController.

    @PostMapping(value = "/crop-cycles/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Attach a photo or document as evidence against a crop cycle")
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

    @GetMapping("/crop-cycles/{id}/evidence")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/crop-cycles/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule(SOURCE_MODULE);
        EvidenceFacade.DownloadedEvidence file = evidenceFacade.download(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/crop-cycles/evidence/{evidenceId}/detach")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> detachEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule(SOURCE_MODULE);
        evidenceFacade.detach(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok(ApiResponse.success("Evidence detached", null));
    }
}
