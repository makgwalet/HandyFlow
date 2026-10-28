package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterCostService;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterReportRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterCostDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter/jobs/{jobId}/costs")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "What a job cost to fill")
public class RecruiterCostController {

    private final RecruiterCostService costs;

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITER_REPORTS')")
    @Operation(summary = "The costs recorded against a job")
    public ResponseEntity<ApiResponse<JobCosts>> list(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(costs.costs(TenantContext.getTenantIdAsObject(), jobId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Record a cost against a job")
    public ResponseEntity<ApiResponse<JobCosts>> add(@PathVariable UUID jobId, @RequestBody CostRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Recorded",
                costs.add(TenantContext.getTenantIdAsObject(), jobId, req, TenantContext.getCurrentUserId(), LocalDate.now(RecruiterReportRules.ZONE))));
    }

    @DeleteMapping("/{costId}")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Remove a recorded cost")
    public ResponseEntity<ApiResponse<JobCosts>> remove(@PathVariable UUID jobId, @PathVariable UUID costId) {
        return ResponseEntity.ok(ApiResponse.success("Removed", costs.remove(TenantContext.getTenantIdAsObject(), jobId, costId)));
    }
}
