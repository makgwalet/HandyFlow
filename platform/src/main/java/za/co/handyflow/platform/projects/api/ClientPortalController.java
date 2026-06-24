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
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Public (unauthenticated) client portal endpoint.
 * No Spring Security filter — secured purely by the opaque token's secrecy.
 * The token is a 32-char hex string generated at project creation and stored
 * in projects.client_portal_token (UNIQUE index).
 *
 * Exposes: project name/status/health, milestones, open snags, red risks only.
 * Never exposes: budget lines, internal notes, change orders, or full risk register.
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
    public ResponseEntity<ApiResponse<ProjectPortalResponse>> getPortal(@PathVariable String token) {

        // Resolve project by token — throws 404 if invalid
        Project p = projectService.getProjectByPortalToken(token);

        // For portal endpoints we construct TenantId from the project's own tenantId.
        // TenantContext is unavailable (no auth filter), so we pass UUID directly
        // to portal-safe service method variants that accept UUID instead of TenantId.
        UUID tid = p.getTenantId();

        // Use the portal-safe methods (they verify the project belongs to this tenant
        // by looking up through the already-resolved Project entity, not TenantContext)
        List<TaskResponse> milestones = projectService
                .getMilestonesForPortal(p.getId())
                .stream().map(TaskResponse::of).toList();

        List<SnagResponse> openSnags = fieldService
                .getSnags(tid, p.getId(), true)
                .stream().map(SnagResponse::of).toList();

        List<RiskResponse> redRisks = projectService
                .getRisksForPortal(p.getId()).stream()
                .filter(r -> "RED".equals(r.getRating()) && "OPEN".equals(r.getStatus()))
                .map(RiskResponse::of).toList();

        // Overall completion = average progress of milestone tasks
        BigDecimal completionPct = milestones.isEmpty()
                ? BigDecimal.ZERO
                : milestones.stream()
                .map(TaskResponse::progressPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(milestones.size()), 2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(ApiResponse.success("Success", new ProjectPortalResponse(
                p.getProjectNumber(), p.getName(), p.getClientName(),
                p.getStatus(), p.getHealth(),
                p.getStartDate(), p.getEndDate(),
                p.getBudgetTotal(), completionPct,
                milestones, openSnags, redRisks)));
    }
}
