package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterPoolService;
import za.co.handyflow.platform.recruiter.dto.RecruiterPoolDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Talent pools and reusing past candidates")
public class RecruiterPoolController {

    private final RecruiterPoolService pools;

    @GetMapping("/pools")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Talent pools with their member counts")
    public ResponseEntity<ApiResponse<List<PoolView>>> list() {
        return ResponseEntity.ok(ApiResponse.success(pools.pools(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/pools")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Create a talent pool")
    public ResponseEntity<ApiResponse<PoolView>> create(@RequestBody PoolRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Created",
                pools.createPool(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/pools/{poolId}")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Delete a pool. The candidates stay")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID poolId) {
        pools.deletePool(TenantContext.getTenantIdAsObject(), poolId);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Find past candidates by name, email, location or tag, optionally within one pool")
    public ResponseEntity<ApiResponse<List<CandidateView>>> candidates(@RequestParam(required = false) String q,
                                                                       @RequestParam(required = false) String tag,
                                                                       @RequestParam(required = false) UUID poolId) {
        return ResponseEntity.ok(ApiResponse.success(pools.candidates(TenantContext.getTenantIdAsObject(), q, tag, poolId)));
    }

    @PutMapping("/candidates/{applicantId}/tags")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Replace a candidate's tags")
    public ResponseEntity<ApiResponse<List<String>>> tags(@PathVariable UUID applicantId, @RequestBody TagsRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Saved", pools.setTags(TenantContext.getTenantIdAsObject(), applicantId, req)));
    }

    @PostMapping("/pools/{poolId}/members")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Add a candidate to a pool, while their consent and retention period allow it")
    public ResponseEntity<ApiResponse<Void>> add(@PathVariable UUID poolId, @RequestBody AddMemberRequest req) {
        pools.addToPool(TenantContext.getTenantIdAsObject(), poolId, req, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Added", null));
    }

    @DeleteMapping("/pools/{poolId}/members/{applicantId}")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Remove a candidate from a pool")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID poolId, @PathVariable UUID applicantId) {
        pools.removeFromPool(TenantContext.getTenantIdAsObject(), poolId, applicantId);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    @PostMapping("/candidates/{applicantId}/apply")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Put a past candidate forward for another open job, without a second record of the person")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> apply(@PathVariable UUID applicantId, @RequestBody ApplyToJobRequest req) {
        if (req == null || req.jobId() == null) throw new za.co.handyflow.platform.shared.HandyFlowException(
                "Choose a job.", org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_POOL");
        UUID id = pools.applyToJob(TenantContext.getTenantIdAsObject(), applicantId, req.jobId(), TenantContext.getCurrentUserId(), "Recruiter");
        return ResponseEntity.status(201).body(ApiResponse.success("Put forward", Map.of("applicationId", id)));
    }
}
