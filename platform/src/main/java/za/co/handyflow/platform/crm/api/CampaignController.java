package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CampaignService;
import za.co.handyflow.platform.crm.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/campaigns")
@RequiredArgsConstructor
@Tag(name = "CRM - Campaigns", description = "Outreach to a list of people, with a journey of timed steps")
public class CampaignController {
    private final CampaignService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The campaigns, with how many people have been contacted, responded and converted")
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a campaign (starts as a draft)")
    public ResponseEntity<ApiResponse<CampaignResponse>> create(@RequestBody CampaignRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a campaign's name, goal or dates")
    public ResponseEntity<ApiResponse<CampaignResponse>> update(@PathVariable UUID id, @RequestBody CampaignRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Start, pause, resume or finish a campaign")
    public ResponseEntity<ApiResponse<CampaignResponse>> setStatus(@PathVariable UUID id, @RequestBody CampaignStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.setStatus(TenantContext.getTenantIdAsObject(), id, request.status())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a campaign (not while it is running)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/steps")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The journey steps")
    public ResponseEntity<ApiResponse<List<JourneyStepResponse>>> steps(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.steps(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/{id}/steps")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Replace the journey steps (the campaign must not be running)")
    public ResponseEntity<ApiResponse<List<JourneyStepResponse>>> setSteps(@PathVariable UUID id, @RequestBody List<JourneyStepRequest> steps) {
        return ResponseEntity.ok(ApiResponse.success(service.setSteps(TenantContext.getTenantIdAsObject(), id, steps)));
    }

    @GetMapping("/{id}/people")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The people in a campaign")
    public ResponseEntity<ApiResponse<List<CampaignMemberResponse>>> people(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.people(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/people")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add people by name, or everyone a segment matches now")
    public ResponseEntity<ApiResponse<AddMembersResponse>> addPeople(@PathVariable UUID id, @RequestBody AddMembersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.addPeople(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/people/{memberId}/status")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Record that someone was contacted, responded or converted")
    public ResponseEntity<ApiResponse<CampaignMemberResponse>> setPersonStatus(@PathVariable UUID id, @PathVariable UUID memberId, @RequestBody CampaignMemberStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.setPersonStatus(TenantContext.getTenantIdAsObject(), id, memberId, request.status())));
    }

    @DeleteMapping("/{id}/people/{memberId}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Take someone out of a campaign")
    public ResponseEntity<ApiResponse<Void>> removePerson(@PathVariable UUID id, @PathVariable UUID memberId) {
        service.removePerson(TenantContext.getTenantIdAsObject(), id, memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The most recent journey steps that ran (default 50, at most 200)")
    public ResponseEntity<ApiResponse<List<JourneyRunResponse>>> runs(@PathVariable UUID id, @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.recentRuns(TenantContext.getTenantIdAsObject(), id, limit)));
    }
}
