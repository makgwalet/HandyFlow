package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.RegulatoryLibraryService;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationCategory;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryLibraryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Regulatory library: instruments, versions, requirements. Writes need LEGALCOMPLIANCE_REGULATION_MANAGE (or ADMIN). */
@RestController
@RequestMapping("/api/v1/legalcompliance/regulatory")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Regulatory Library", description = "Instruments, versions and requirements")
public class RegulatoryLibraryController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_EDIT = "hasAnyAuthority('LEGALCOMPLIANCE_REGULATION_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final RegulatoryLibraryService library;
    private final FeatureGuard featureGuard;

    // ── Instruments ───────────────────────────────────────────────────────────

    @GetMapping("/instruments")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Search the catalogue and your own instruments (scope ALL | PLATFORM | MINE)")
    public ResponseEntity<ApiResponse<Page<InstrumentResponse>>> search(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) ObligationCategory topic,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(library.search(tenant(), scope, topic, q, pageable)));
    }

    @GetMapping("/instruments/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<InstrumentDetailResponse>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(library.detail(tenant(), id)));
    }

    @PostMapping("/instruments")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<InstrumentResponse>> create(@Valid @RequestBody InstrumentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Instrument created",
                library.create(tenant(), uid(), uname(), req)));
    }

    @PutMapping("/instruments/{id}")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Edit your own instrument (resets its review status)")
    public ResponseEntity<ApiResponse<InstrumentResponse>> update(@PathVariable UUID id, @Valid @RequestBody InstrumentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Instrument updated", library.update(tenant(), id, uid(), uname(), req)));
    }

    @PostMapping("/instruments/{id}/adopt")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Copy a catalogue entry into your own library (idempotent)")
    public ResponseEntity<ApiResponse<InstrumentResponse>> adopt(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Instrument adopted", library.adopt(tenant(), id, uid(), uname())));
    }

    @PostMapping("/instruments/{id}/mark-reviewed")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<InstrumentResponse>> markReviewed(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Marked reviewed", library.markReviewed(tenant(), id, uid(), uname())));
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @PostMapping("/instruments/{id}/versions")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Start a DRAFT version, optionally carrying forward an earlier version's requirements")
    public ResponseEntity<ApiResponse<VersionResponse>> createVersion(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft version created",
                library.createVersion(tenant(), id, uid(), uname(), req)));
    }

    @PutMapping("/versions/{versionId}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<VersionResponse>> updateVersion(@PathVariable UUID versionId, @Valid @RequestBody VersionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Draft updated", library.updateVersion(tenant(), versionId, uid(), uname(), req)));
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Publish a draft: it becomes CURRENT, the previous CURRENT is superseded, and the text becomes immutable")
    public ResponseEntity<ApiResponse<VersionResponse>> publish(@PathVariable UUID versionId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Version published", library.publish(tenant(), versionId, uid(), uname())));
    }

    @PostMapping("/versions/{versionId}/repeal")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<VersionResponse>> repeal(@PathVariable UUID versionId, @Valid @RequestBody RepealRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Version repealed", library.repeal(tenant(), versionId, req, uid(), uname())));
    }

    // ── Requirements ──────────────────────────────────────────────────────────

    @GetMapping("/versions/{versionId}/requirements")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<RequirementResponse>>> requirements(@PathVariable UUID versionId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(library.listRequirements(tenant(), versionId)));
    }

    @PostMapping("/versions/{versionId}/requirements")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<RequirementResponse>> addRequirement(@PathVariable UUID versionId, @Valid @RequestBody RequirementRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement added",
                library.addRequirement(tenant(), versionId, uid(), uname(), req)));
    }

    @PutMapping("/requirements/{requirementId}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<RequirementResponse>> updateRequirement(@PathVariable UUID requirementId, @Valid @RequestBody RequirementRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Requirement updated", library.updateRequirement(tenant(), requirementId, uid(), uname(), req)));
    }

    @DeleteMapping("/requirements/{requirementId}")
    @PreAuthorize(CAN_EDIT)
    public ResponseEntity<ApiResponse<Void>> deleteRequirement(@PathVariable UUID requirementId) {
        featureGuard.requireModule("legalcompliance");
        library.deleteRequirement(tenant(), requirementId, uid(), uname());
        return ResponseEntity.ok(ApiResponse.success("Requirement removed from draft", null));
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    @GetMapping("/events")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Audit trail for one INSTRUMENT, VERSION, REQUIREMENT, ASSESSMENT or OBLIGATION")
    public ResponseEntity<ApiResponse<List<EventResponse>>> events(@RequestParam String entityType, @RequestParam UUID entityId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(library.events(tenant(), entityType, entityId)));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
