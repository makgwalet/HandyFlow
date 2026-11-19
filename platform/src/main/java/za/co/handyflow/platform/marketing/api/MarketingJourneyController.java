package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingJourneyService;
import za.co.handyflow.platform.marketing.dto.JourneyDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing/journeys")
@RequiredArgsConstructor
@Tag(name = "Marketing Journeys", description = "Automated multi-step email journeys")
public class MarketingJourneyController {

    private final MarketingJourneyService journeys;

    @GetMapping
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<JourneyResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(journeys.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<JourneyResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(journeys.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<JourneyResponse>> create(@Valid @RequestBody JourneyRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Journey created",
                journeys.create(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Edit a draft journey (steps, trigger, pacing)")
    public ResponseEntity<ApiResponse<JourneyResponse>> update(@PathVariable UUID id, @Valid @RequestBody JourneyRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Journey updated",
                journeys.update(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }

    @PutMapping("/{id}/settings")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Name, description, pacing and send window: allowed in any non-archived state")
    public ResponseEntity<ApiResponse<JourneyResponse>> settings(@PathVariable UUID id, @Valid @RequestBody JourneySettingsRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Settings saved",
                journeys.updateSettings(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }

    @GetMapping("/{id}/preview-activation")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Who would be enrolled on activation, plus content issues")
    public ResponseEntity<ApiResponse<ActivationPreview>> preview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(journeys.previewActivation(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Activate a draft (admin): runs the content checker, creates the step campaigns, freezes the steps")
    public ResponseEntity<ApiResponse<JourneyResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Journey activated",
                journeys.activate(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id)));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<JourneyResponse>> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Journey paused", journeys.pause(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<JourneyResponse>> resume(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Journey resumed", journeys.resume(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Archive a journey and cancel its active enrolments")
    public ResponseEntity<ApiResponse<JourneyResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Journey archived", journeys.archive(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<JourneyResponse>> clone(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(ApiResponse.success("Journey cloned as a draft",
                journeys.clone(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id)));
    }

    @PostMapping("/{id}/resume-steps")
    @PreAuthorize("hasAuthority('MARKETING_ADMIN')")
    @Operation(summary = "Resume step campaigns the delivery engine auto-paused (bounce/complaint thresholds)")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> resumeSteps(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("resumed", journeys.resumePausedSteps(TenantContext.getTenantIdAsObject(), id))));
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<JourneyStats>> stats(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(journeys.stats(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/enrollments")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<Page<EnrollmentRow>>> enrollments(
            @PathVariable UUID id, @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String search, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                journeys.enrollments(TenantContext.getTenantIdAsObject(), id, status, search, pageable)));
    }

    @GetMapping("/{id}/enrollments/{enrollmentId}/log")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<LogEntry>>> log(@PathVariable UUID id, @PathVariable UUID enrollmentId) {
        return ResponseEntity.ok(ApiResponse.success(journeys.log(TenantContext.getTenantIdAsObject(), id, enrollmentId)));
    }

    @PostMapping("/{id}/enrollments/{enrollmentId}/cancel")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Remove one contact from the journey")
    public ResponseEntity<ApiResponse<Void>> cancelEnrollment(@PathVariable UUID id, @PathVariable UUID enrollmentId) {
        journeys.cancelEnrollment(TenantContext.getTenantIdAsObject(), id, enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("Contact removed from journey", null));
    }
}
