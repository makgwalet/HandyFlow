package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvCatalogueService;
import za.co.handyflow.platform.trainingprovider.dto.CatalogueDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/catalogue")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Catalogue", description = "Qualifications, programmes, versions, modules and offerings")
public class TrainProvCatalogueController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvCatalogueService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    // qualifications

    @GetMapping("/qualifications")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<QualificationView>>> qualifications(@RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.qualifications(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/qualifications/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<QualificationDetail>> qualification(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.qualification(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/qualifications")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QualificationView>> createQualification(@Valid @RequestBody QualificationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Qualification added",
                service.createQualification(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/qualifications/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QualificationView>> updateQualification(@PathVariable UUID id, @Valid @RequestBody QualificationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Qualification updated", service.updateQualification(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/qualifications/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<QualificationView>> retireQualification(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Qualification retired",
                service.retireQualification(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/qualifications/{id}/versions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QualificationVersionView>> newQualificationVersion(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Draft version started",
                service.newQualificationVersion(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/qualification-versions/{versionId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<QualificationVersionView>> editQualificationVersion(@PathVariable UUID versionId,
                                                                                         @Valid @RequestBody QualificationVersionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Version updated",
                service.editQualificationVersion(TenantContext.getTenantIdAsObject(), versionId, r)));
    }

    @PostMapping("/qualification-versions/{versionId}/make-current")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<QualificationVersionView>> makeCurrent(@PathVariable UUID versionId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Version is now current",
                service.makeQualificationVersionCurrent(TenantContext.getTenantIdAsObject(), versionId, TenantContext.getCurrentUserId(), today())));
    }

    // programmes

    @GetMapping("/programmes")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ProgrammeView>>> programmes(@RequestParam(required = false) String status,
                                                                       @RequestParam(required = false) String activity) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.programmes(TenantContext.getTenantIdAsObject(), status, activity, today())));
    }

    @GetMapping("/programmes/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ProgrammeDetail>> programme(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.programme(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/programmes")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeView>> createProgramme(@Valid @RequestBody ProgrammeRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Programme added",
                service.createProgramme(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/programmes/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeView>> updateProgramme(@PathVariable UUID id, @Valid @RequestBody ProgrammeRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Programme updated", service.updateProgramme(TenantContext.getTenantIdAsObject(), id, r, today())));
    }

    @PostMapping("/programmes/{id}/activate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeView>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Programme activated", service.activateProgramme(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/programmes/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProgrammeView>> retireProgramme(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Programme retired",
                service.retireProgramme(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId(), today())));
    }

    // versions and modules

    @PostMapping("/programmes/{id}/versions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeVersionView>> newVersion(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Draft version started",
                service.newProgrammeVersion(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/programme-versions/{versionId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeVersionView>> editVersion(@PathVariable UUID versionId, @Valid @RequestBody ProgrammeVersionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Version updated", service.editProgrammeVersion(TenantContext.getTenantIdAsObject(), versionId, r)));
    }

    @PostMapping("/programme-versions/{versionId}/modules")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeVersionView>> addModule(@PathVariable UUID versionId, @Valid @RequestBody ModuleRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Module added", service.addModule(TenantContext.getTenantIdAsObject(), versionId, r)));
    }

    @DeleteMapping("/modules/{moduleId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgrammeVersionView>> removeModule(@PathVariable UUID moduleId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Module removed from the draft", service.removeModule(TenantContext.getTenantIdAsObject(), moduleId)));
    }

    @PostMapping("/programme-versions/{versionId}/approve")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ProgrammeVersionView>> approve(@PathVariable UUID versionId, @RequestBody(required = false) ApproveVersionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Version approved",
                service.approveVersion(TenantContext.getTenantIdAsObject(), versionId, r == null ? null : r.overrideReason(), TenantContext.getCurrentUserId())));
    }

    // offerings

    @PostMapping("/programmes/{id}/offerings")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<OfferingView>> addOffering(@PathVariable UUID id, @Valid @RequestBody OfferingRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Offering added",
                service.addOffering(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/offerings/{offeringId}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<OfferingView>> withdraw(@PathVariable UUID offeringId, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Offering withdrawn",
                service.withdrawOffering(TenantContext.getTenantIdAsObject(), offeringId, r.reason(), TenantContext.getCurrentUserId(), today())));
    }
}
