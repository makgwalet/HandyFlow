package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.FieldService;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Public (unauthenticated) client portal endpoint.
 *
 * Security model: secured purely by the opaque token's secrecy.
 * The token is a 32-char hex string (UUID without dashes) generated at project
 * creation and stored in projects.client_portal_token (UNIQUE index).
 *
 * No Spring Security filter on this controller — it is explicitly excluded in
 * SecurityConfig.  Add rate-limiting at the gateway/reverse-proxy level.
 *
 * Exposes: project name/status/health, milestones, open snags, red risks only.
 * Never exposes: budget lines, internal notes, change orders, or full risk register.
 *
 * CHANGE: portal-safe service calls now pass the resolved Project entity instead
 * of raw UUIDs, closing the tenant isolation gap in FieldService.
 */
@RestController
@RequestMapping("/api/public/projects/portal")
@RequiredArgsConstructor
@Tag(name = "Client Portal", description = "Public read-only project view secured by opaque token")
public class ClientPortalController {

    private final ProjectService projectService;
    private final FieldService   fieldService;

    @GetMapping("/{token}")
    @Operation(summary = "Retrieve project portal view by token — no authentication required")
    public ResponseEntity<ApiResponse<ProjectPortalResponse>> getPortal(
            @PathVariable String token) {

        // 1. Resolve project by token — throws 404 if invalid or expired
        Project p = projectService.getProjectByPortalToken(token);

        // 2. Fetch portal-safe data passing the resolved entity (not raw UUIDs).
        //    FieldService.getSnagsForPortal(Project) requires the entity, proving
        //    the caller went through the authenticated token resolution path.
        List<TaskResponse> milestones = projectService
                .getMilestonesForPortal(p)              // FIX: pass entity, not p.getId()
                .stream().map(TaskResponse::of).toList();

        List<SnagResponse> openSnags = fieldService
                .getSnagsForPortal(p, true)             // FIX: entity-based, no tenant UUID
                .stream().map(SnagResponse::of).toList();

        List<RiskResponse> redRisks = projectService
                .getRisksForPortal(p)                   // FIX: pass entity, not p.getId()
                .stream()
                .filter(r -> "RED".equals(r.getRating()) && "OPEN".equals(r.getStatus()))
                .map(RiskResponse::of).toList();

        // 3. Compute overall completion from milestone task progress
        BigDecimal completionPct = milestones.isEmpty()
                ? BigDecimal.ZERO
                : milestones.stream()
                .map(TaskResponse::progressPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(milestones.size()), 2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(ApiResponse.success("Success",
                new ProjectPortalResponse(
                        p.getProjectNumber(), p.getName(), p.getClientName(),
                        p.getStatus().name(),   // enum → string for the response DTO
                        p.getHealth().name(),
                        p.getStartDate(), p.getEndDate(),
                        p.getBudgetTotal(), completionPct,
                        milestones, openSnags, redRisks)));
    }
}
