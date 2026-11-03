package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.DocumentService;
import za.co.handyflow.platform.projects.application.internal.FieldService;
import za.co.handyflow.platform.projects.application.internal.ProjectPortalService;
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
 * Security model: the opaque token IS the credential, so around it:
 *  - ProjectPortalService.resolve(): per-IP rate limit, strict token format, and
 *    enabled/expiry checks; every failure is the same 404.
 *  - Staff can rotate, revoke and re-enable the token (ProjectPortalAdminController).
 *  - The payload uses client-safe DTOs — no budget, cost, hours, assignee, mitigation.
 *  - Responses are marked no-store / noindex / no-referrer: the token is in the URL
 *    path, so caches, crawlers and Referer headers must never carry it elsewhere.
 *
 * DEPLOYMENT NOTE: rate limiting keys on request.getRemoteAddr(). Behind a load
 * balancer or reverse proxy that is the proxy's address unless you configure
 * server.forward-headers-strategy (native / framework) so Spring resolves the real
 * client IP — otherwise every client shares one bucket.
 */
@RestController
@RequestMapping("/api/public/projects/portal")
@RequiredArgsConstructor
@Tag(name = "Client Portal", description = "Public read-only project view secured by opaque token")
public class ClientPortalController {

    private final ProjectPortalService portalService;
    private final ProjectService       projectService;
    private final FieldService         fieldService;
    private final DocumentService      documentService;

    @GetMapping("/{token}")
    @Operation(summary = "Retrieve project portal view by token — no authentication required")
    public ResponseEntity<ApiResponse<ProjectPortalResponse>> getPortal(
            @PathVariable String token, HttpServletRequest request) {

        Project p = portalService.resolve(token, request.getRemoteAddr());

        List<PortalMilestoneResponse> milestones = projectService
                .getMilestonesForPortal(p)
                .stream().map(PortalMilestoneResponse::of).toList();

        List<PortalSnagResponse> openSnags = fieldService
                .getSnagsForPortal(p, true)
                .stream().map(PortalSnagResponse::of).toList();

        List<PortalRiskResponse> redRisks = projectService
                .getRisksForPortal(p)
                .stream()
                .filter(r -> "RED".equals(r.getRating()) && "OPEN".equals(r.getStatus()))
                .map(PortalRiskResponse::of).toList();

        BigDecimal completionPct = milestones.isEmpty()
                ? BigDecimal.ZERO
                : milestones.stream()
                .map(PortalMilestoneResponse::progressPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(milestones.size()), 2, RoundingMode.HALF_UP);

        List<PortalDocumentResponse> documents = documentService
                .getClientVisibleForPortal(p)
                .stream().map(PortalDocumentResponse::of).toList();

        ProjectPortalResponse body = new ProjectPortalResponse(
                p.getProjectNumber(), p.getName(), p.getClientName(),
                p.getStatus().name(), p.getHealth().name(),
                p.getStartDate(), p.getEndDate(), completionPct,
                milestones, openSnags, redRisks, documents);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success("Success", body));
    }
}