package za.co.handyflow.platform.payrollbureau.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.payrollbureau.application.internal.PayrollBureauPortalDataService;
import za.co.handyflow.platform.payrollbureau.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.payrollbureau.dto.PortalPayRunDtos;

/**
 * The missing piece — same gap as RecruitmentAgencyPortalDataController
 * and the earlier-fixed PayrollBureauPortalAuthController.
 * PayrollBureauPortalDataService.getMyClients() confirmed directly
 * against real source; getMyFeeNotes/getMyDeadlines inferred from the
 * sibling pattern and the already-fixed payrollBureauPortal.api.ts
 * frontend, not directly confirmed the way getMyClients was.
 */
@RestController
@RequestMapping("/api/v1/payroll-bureau/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Payroll Bureau Client Portal", description = "Client-facing data access")
public class PayrollBureauPortalDataController {

    private final PayrollBureauPortalDataService portalDataService;

    @GetMapping("/clients")
    @Operation(summary = "List every client this portal user has access to")
    public ResponseEntity<ApiResponse<List<PortalClientSummaryResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyClients(getPortalUserId())));
    }

    @GetMapping("/clients/{clientId}/fee-notes")
    @Operation(summary = "List fee notes for a client this portal user has access to")
    public ResponseEntity<ApiResponse<Page<PayFeeNoteResponse>>> getMyFeeNotes(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyFeeNotes(getPortalUserId(), clientId, pageable)));
    }

    @GetMapping("/clients/{clientId}/deadlines")
    @Operation(summary = "List SARS deadlines for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<PayDeadlineResponse>>> getMyDeadlines(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyDeadlines(getPortalUserId(), clientId)));
    }

    @GetMapping("/clients/{clientId}/pay-runs")
    @Operation(summary = "Pay runs sent to this client for approval, and finalised ones")
    public ResponseEntity<ApiResponse<List<PortalPayRunDtos.PortalPayRun>>> getMyPayRuns(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyPayRuns(getPortalUserId(), clientId)));
    }

    @GetMapping("/clients/{clientId}/pay-runs/{payRunId}")
    @Operation(summary = "One pay run with each employee's payslip figures and the checks against the last run")
    public ResponseEntity<ApiResponse<PortalPayRunDtos.PortalPayRunDetail>> getMyPayRun(@PathVariable UUID clientId, @PathVariable UUID payRunId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyPayRun(getPortalUserId(), clientId, payRunId)));
    }

    @PostMapping("/clients/{clientId}/pay-runs/{payRunId}/approve")
    @Operation(summary = "Approve a pay run that was sent to this client")
    public ResponseEntity<ApiResponse<PortalPayRunDtos.PortalPayRun>> approveMyPayRun(@PathVariable UUID clientId, @PathVariable UUID payRunId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.approveMyPayRun(getPortalUserId(), clientId, payRunId)));
    }

    @PostMapping("/clients/{clientId}/pay-runs/{payRunId}/decline")
    @Operation(summary = "Decline a pay run with a reason; it goes back to the bureau as a draft")
    public ResponseEntity<ApiResponse<PortalPayRunDtos.PortalPayRun>> declineMyPayRun(@PathVariable UUID clientId, @PathVariable UUID payRunId,
                                                                                    @RequestBody PortalPayRunDtos.DeclineRequest req) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.declineMyPayRun(getPortalUserId(), clientId, payRunId, req == null ? null : req.reason())));
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}