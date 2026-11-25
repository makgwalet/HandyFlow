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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvCohortService;
import za.co.handyflow.platform.trainingprovider.dto.CohortDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Cohorts", description = "Venues, cohorts, cohort members and facilitators")
public class TrainProvCohortController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvCohortService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    // ---- venues ----

    @GetMapping("/venues")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<VenueView>>> venues(@RequestParam(required = false) UUID siteId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.venues(TenantContext.getTenantIdAsObject(), siteId, status)));
    }

    @PostMapping("/venues")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VenueView>> createVenue(@Valid @RequestBody VenueRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Venue added", service.createVenue(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/venues/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VenueView>> updateVenue(@PathVariable UUID id, @Valid @RequestBody VenueRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Venue updated", service.updateVenue(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/venues/{id}/deactivate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VenueView>> deactivateVenue(@PathVariable UUID id, @Valid @RequestBody Reason r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Venue made inactive", service.deactivateVenue(TenantContext.getTenantIdAsObject(), id, r.reason())));
    }

    @PostMapping("/venues/{id}/reactivate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VenueView>> reactivateVenue(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Venue active again", service.reactivateVenue(TenantContext.getTenantIdAsObject(), id)));
    }

    // ---- cohorts ----

    @GetMapping("/cohorts")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<CohortView>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID programmeId,
                                                              @RequestParam(required = false) UUID siteId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, programmeId, siteId)));
    }

    @GetMapping("/cohorts/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CohortDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/cohorts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortView>> create(@Valid @RequestBody CohortRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Cohort opened", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/cohorts/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortView>> update(@PathVariable UUID id, @Valid @RequestBody CohortUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Cohort updated", service.update(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/cohorts/{id}/transition")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortView>> transition(@PathVariable UUID id, @Valid @RequestBody CohortTransition r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Cohort updated", service.transition(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/cohorts/{id}/members")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortDetail>> addMember(@PathVariable UUID id, @Valid @RequestBody AddMember r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner added to the cohort",
                service.addMember(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/cohort-members/{memberId}/remove")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortDetail>> removeMember(@PathVariable UUID memberId, @Valid @RequestBody Reason r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner removed from the cohort",
                service.removeMember(TenantContext.getTenantIdAsObject(), memberId, r.reason(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/cohorts/{id}/facilitators")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortDetail>> assignFacilitator(@PathVariable UUID id, @Valid @RequestBody AssignFacilitator r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Facilitator assigned",
                service.assignFacilitator(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/cohort-facilitators/{assignmentId}/remove")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CohortDetail>> removeFacilitator(@PathVariable UUID assignmentId, @Valid @RequestBody Reason r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Facilitator removed",
                service.removeFacilitator(TenantContext.getTenantIdAsObject(), assignmentId, r.reason(), TenantContext.getCurrentUserId(), today())));
    }
}
