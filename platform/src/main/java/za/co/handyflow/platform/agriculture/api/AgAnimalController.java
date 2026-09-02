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
import za.co.handyflow.platform.agriculture.application.internal.AgAnimalService;
import za.co.handyflow.platform.agriculture.application.internal.AgBreedingRecordService;
import za.co.handyflow.platform.agriculture.application.internal.AgFeedRecordService;
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
 * Individually-tracked animals plus every sub-resource that hangs off one —
 * weight, health, breeding, movement, mortality, feed history and evidence
 * photos — consolidated onto one controller the same way
 * {@code EarthAssetController} nests maintenance/operator-logs under the
 * asset rather than giving each its own top-level controller.
 * <p>
 * The health-event and breeding-record TRANSITION endpoints (complete/
 * acknowledge/confirm-pregnant/record-birth/etc.) are addressed by the
 * record's own id alone — they work identically whether the record is
 * linked to an animal or a group, so they live here rather than being
 * duplicated onto {@code AgGroupController} as well; a caller only needs
 * the record id returned from either controller's own create/list calls.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Animals", description = "Individually-tracked livestock and their full history")
public class AgAnimalController {

    private final AgAnimalService animalService;
    private final AgHealthEventService healthEventService;
    private final AgBreedingRecordService breedingRecordService;
    private final AgMovementRecordService movementRecordService;
    private final AgMortalityRecordService mortalityRecordService;
    private final AgFeedRecordService feedRecordService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    private static final String SOURCE_MODULE = "agriculture";
    private static final String ENTITY_TYPE = "AgAnimal";

    // ── Animals ──────────────────────────────────────────────────────────

    @GetMapping("/farms/{farmId}/animals")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<AnimalResponse>>> getAnimalsForFarm(
            @PathVariable UUID farmId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                animalService.getAnimalsForFarm(TenantContext.getTenantIdAsObject(), farmId, status, pageable)));
    }

    @PostMapping("/farms/{farmId}/animals")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Register a new individually-tracked animal")
    public ResponseEntity<ApiResponse<AnimalResponse>> createAnimal(
            @PathVariable UUID farmId, @Valid @RequestBody CreateAnimalRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Animal registered",
                animalService.createAnimal(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/animals/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<AnimalResponse>> getAnimal(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                animalService.getAnimal(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/animals/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<AnimalResponse>> updateAnimal(
            @PathVariable UUID id, @Valid @RequestBody UpdateAnimalRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Animal updated",
                animalService.updateAnimal(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/animals/{id}/move")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<AnimalResponse>> moveAnimal(
            @PathVariable UUID id, @RequestBody MoveAnimalRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Animal moved",
                animalService.moveAnimal(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/animals/{id}/status")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<AnimalResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeAnimalStatusRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                animalService.changeStatus(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/animals/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAnimal(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        animalService.deleteAnimal(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Animal deleted", null));
    }

    // ── Weight history ───────────────────────────────────────────────────

    @GetMapping("/animals/{id}/weight-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<WeightRecordResponse>>> getWeightHistory(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                animalService.getWeightHistory(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/animals/{id}/weight-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record a weight measurement — also updates the animal's current weight")
    public ResponseEntity<ApiResponse<WeightRecordResponse>> recordWeight(
            @PathVariable UUID id, @Valid @RequestBody RecordWeightRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Weight recorded",
                animalService.recordWeight(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Health events ────────────────────────────────────────────────────

    @GetMapping("/animals/{id}/health-events")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<HealthEventResponse>>> getHealthEventsForAnimal(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                healthEventService.getHistoryForAnimal(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/animals/{id}/health-events")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<HealthEventResponse>> createHealthEventForAnimal(
            @PathVariable UUID id, @Valid @RequestBody CreateHealthEventRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Health event recorded",
                healthEventService.createHealthEvent(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/health-events/{eventId}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<HealthEventResponse>> getHealthEvent(@PathVariable UUID eventId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                healthEventService.getHealthEvent(TenantContext.getTenantIdAsObject(), eventId)));
    }

    @PutMapping("/health-events/{eventId}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<HealthEventResponse>> updateHealthEvent(
            @PathVariable UUID eventId, @Valid @RequestBody UpdateHealthEventRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Health event updated",
                healthEventService.updateHealthEvent(TenantContext.getTenantIdAsObject(), eventId, request)));
    }

    @PatchMapping("/health-events/{eventId}/complete")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<HealthEventResponse>> markHealthEventCompleted(@PathVariable UUID eventId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Marked completed",
                healthEventService.markCompleted(TenantContext.getTenantIdAsObject(), eventId)));
    }

    @PatchMapping("/health-events/{eventId}/acknowledge")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Acknowledge a due-date reminder without recording another completed event")
    public ResponseEntity<ApiResponse<HealthEventResponse>> acknowledgeHealthEventReminder(@PathVariable UUID eventId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Reminder acknowledged",
                healthEventService.acknowledgeReminder(TenantContext.getTenantIdAsObject(), eventId)));
    }

    // ── Breeding records ─────────────────────────────────────────────────

    @GetMapping("/animals/{id}/breeding-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<BreedingRecordResponse>>> getBreedingRecordsForAnimal(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                breedingRecordService.getHistoryForAnimal(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/animals/{id}/breeding-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> createBreedingRecordForAnimal(
            @PathVariable UUID id, @Valid @RequestBody CreateBreedingRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Breeding record created",
                breedingRecordService.createBreedingRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/breeding-records/{recordId}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> getBreedingRecord(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                breedingRecordService.getBreedingRecord(TenantContext.getTenantIdAsObject(), recordId)));
    }

    @PatchMapping("/breeding-records/{recordId}/confirm-pregnant")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> confirmPregnant(
            @PathVariable UUID recordId, @RequestBody ConfirmPregnantRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Marked confirmed pregnant",
                breedingRecordService.confirmPregnant(TenantContext.getTenantIdAsObject(), recordId, request)));
    }

    @PatchMapping("/breeding-records/{recordId}/not-pregnant")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> markNotPregnant(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Marked not pregnant",
                breedingRecordService.markNotPregnant(TenantContext.getTenantIdAsObject(), recordId)));
    }

    @PatchMapping("/breeding-records/{recordId}/record-birth")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> recordBirth(
            @PathVariable UUID recordId, @Valid @RequestBody RecordBirthRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Birth recorded",
                breedingRecordService.recordBirth(TenantContext.getTenantIdAsObject(), recordId, request)));
    }

    @PatchMapping("/breeding-records/{recordId}/aborted")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> markAborted(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Marked aborted",
                breedingRecordService.markAborted(TenantContext.getTenantIdAsObject(), recordId)));
    }

    @PatchMapping("/breeding-records/{recordId}/failed")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<BreedingRecordResponse>> markFailed(@PathVariable UUID recordId) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success("Marked failed",
                breedingRecordService.markFailed(TenantContext.getTenantIdAsObject(), recordId)));
    }

    // ── Movement history ─────────────────────────────────────────────────

    @GetMapping("/animals/{id}/movement-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<MovementRecordResponse>>> getMovementRecordsForAnimal(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                movementRecordService.getHistoryForAnimal(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/animals/{id}/movement-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<MovementRecordResponse>> createMovementRecordForAnimal(
            @PathVariable UUID id, @Valid @RequestBody CreateMovementRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Movement recorded",
                movementRecordService.createMovementRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Mortality ────────────────────────────────────────────────────────

    @GetMapping("/animals/{id}/mortality-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<MortalityRecordResponse>>> getMortalityRecordsForAnimal(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                mortalityRecordService.getHistoryForAnimal(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/animals/{id}/mortality-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record a death — also sets the animal's status to DECEASED")
    public ResponseEntity<ApiResponse<MortalityRecordResponse>> createMortalityRecordForAnimal(
            @PathVariable UUID id, @Valid @RequestBody CreateMortalityRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Mortality recorded",
                mortalityRecordService.createMortalityRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Feed history ─────────────────────────────────────────────────────

    @GetMapping("/animals/{id}/feed-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<FeedRecordResponse>>> getFeedRecordsForAnimal(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                feedRecordService.getHistoryForAnimal(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/animals/{id}/feed-records")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<FeedRecordResponse>> createFeedRecordForAnimal(
            @PathVariable UUID id, @Valid @RequestBody CreateFeedRecordRequest request) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Feed recorded",
                feedRecordService.createFeedRecord(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Evidence (scouting/treatment photos) ────────────────────────────
    // Generic passthrough to EvidenceFacade, mirroring TrainingSessionController.

    @PostMapping(value = "/animals/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Attach a photo or document as evidence against an animal")
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

    @GetMapping("/animals/{id}/evidence")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule(SOURCE_MODULE);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/animals/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule(SOURCE_MODULE);
        EvidenceFacade.DownloadedEvidence file = evidenceFacade.download(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/animals/evidence/{evidenceId}/detach")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> detachEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule(SOURCE_MODULE);
        evidenceFacade.detach(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok(ApiResponse.success("Evidence detached", null));
    }
}
