package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractRedlineService;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Version history and redlining. The module entitlement check covers /api/v1/contracts (ModuleEntitlementPaths). */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract versions and redlining")
public class ContractRedlineController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractRedlineService service;

    @GetMapping("/{contractId}/versions")
    @PreAuthorize(READ)
    @Operation(summary = "Body versions of a contract, newest first")
    public ResponseEntity<ApiResponse<List<VersionInfo>>> versions(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Versions", service.versions(TenantContext.getTenantIdAsObject(), contractId)));
    }

    @GetMapping("/{contractId}/versions/compare")
    @PreAuthorize(READ)
    @Operation(summary = "Compare two versions block by block")
    public ResponseEntity<ApiResponse<DiffResponse>> compare(@PathVariable UUID contractId, @RequestParam int from, @RequestParam int to) {
        return ResponseEntity.ok(ApiResponse.success("Comparison", service.compare(TenantContext.getTenantIdAsObject(), contractId, from, to)));
    }

    @GetMapping("/{contractId}/versions/{versionNo}")
    @PreAuthorize(READ)
    @Operation(summary = "The body of one version")
    public ResponseEntity<ApiResponse<VersionBody>> version(@PathVariable UUID contractId, @PathVariable int versionNo) {
        return ResponseEntity.ok(ApiResponse.success("Version", service.version(TenantContext.getTenantIdAsObject(), contractId, versionNo)));
    }

    @GetMapping("/{contractId}/change-proposals")
    @PreAuthorize(READ)
    @Operation(summary = "Change proposals on a contract, newest first, each with its block-level comparison")
    public ResponseEntity<ApiResponse<List<ProposalView>>> proposals(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Change proposals", service.proposals(TenantContext.getTenantIdAsObject(), contractId)));
    }

    @PostMapping("/{contractId}/change-proposals")
    @PreAuthorize(WRITE)
    @Operation(summary = "Propose a revised body for a draft or a contract under review")
    public ResponseEntity<ApiResponse<ProposalView>> propose(@PathVariable UUID contractId, @Valid @RequestBody ProposalRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Change proposed",
                service.propose(TenantContext.getTenantIdAsObject(), contractId, req.body(), req.note(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/change-proposals/{proposalId}")
    @PreAuthorize(READ)
    @Operation(summary = "One change proposal with its comparison")
    public ResponseEntity<ApiResponse<ProposalView>> proposal(@PathVariable UUID proposalId) {
        return ResponseEntity.ok(ApiResponse.success("Change proposal", service.proposal(TenantContext.getTenantIdAsObject(), proposalId)));
    }

    @PostMapping("/change-proposals/{proposalId}/apply")
    @PreAuthorize(WRITE)
    @Operation(summary = "Apply the accepted changes of a proposal. Unaccepted changes are left out; a new version is recorded")
    public ResponseEntity<ApiResponse<ProposalView>> apply(@PathVariable UUID proposalId, @RequestBody ApplyRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Changes applied", service.apply(TenantContext.getTenantIdAsObject(), proposalId,
                req.acceptedHunks(), req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/change-proposals/{proposalId}/reject")
    @PreAuthorize(WRITE)
    @Operation(summary = "Reject a proposal, with the reason")
    public ResponseEntity<ApiResponse<ProposalView>> reject(@PathVariable UUID proposalId, @Valid @RequestBody RejectRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Proposal rejected", service.reject(TenantContext.getTenantIdAsObject(), proposalId,
                req.reason(), TenantContext.getCurrentUserId())));
    }
}
