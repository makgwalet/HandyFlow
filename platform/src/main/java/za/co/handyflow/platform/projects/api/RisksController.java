package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Risk register — CRUD and status transitions.
 *
 * SPLIT FROM RiskDocumentController:
 * The original combined risks and documents in one controller.  Splitting them
 * follows single-responsibility and makes the route structure cleaner:
 *   /api/v1/projects/{projectId}/risks      ← this controller
 *   /api/v1/projects/{projectId}/documents  ← DocumentsController
 *
 * CHANGES FROM ORIGINAL:
 * 1. @Validated + @Valid on all request bodies.
 * 2. Risk number now auto-assigned by SequenceService (handled in ProjectService).
 * 3. Risk owner name: the DTO carries ownerName from the caller — the controller
 *    does not try to resolve it from TenantContext because risks are often owned
 *    by named external parties (inspectors, consultants, subcontractors) who are
 *    not system users.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Risks", description = "Project risk register — probability × impact scoring, OHSA tracking")
public class RisksController {

    private final ProjectService projectService;

    @GetMapping("/{projectId}/risks")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Risk register — ordered by risk_score DESC (highest risk first)")
    public ResponseEntity<ApiResponse<List<RiskResponse>>> getRisks(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getRisks(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(RiskResponse::of).toList()));
    }

    @PostMapping("/{projectId}/risks")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log a risk — rating (GREEN/AMBER/RED) computed from probability × impact")
    public ResponseEntity<ApiResponse<RiskResponse>> createRisk(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateRiskRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Risk logged",
                        RiskResponse.of(projectService.createRisk(
                                TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    /**
     * Risk status actions: MITIGATE | CLOSE | ACCEPT.
     *
     * Body: { "action": "MITIGATE", "notes": "Added safety barriers to all access points" }
     *
     * Each action changes risk.status and records the notes as the mitigation text.
     * A future event system would fire a notification to the risk owner.
     */
    @PostMapping("/risks/{riskId}/action")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Update risk status: MITIGATE | CLOSE | ACCEPT")
    public ResponseEntity<ApiResponse<RiskResponse>> updateRiskStatus(
            @PathVariable UUID riskId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Risk updated",
                RiskResponse.of(projectService.updateRiskStatus(
                        TenantContext.getTenantIdAsObject(),
                        riskId, body.get("action"), body.get("notes")))));
    }
}
