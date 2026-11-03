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
import za.co.handyflow.platform.projects.application.internal.ProjectCostService;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService.CostPosting;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService.PostedEntry;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService.TransferResult;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cost control: the cost position, the ledger, revisions, transfers, forecasts.
 *
 * Every route here contains "/budget/", so ProjectAccessInterceptor already restricts them to
 * project roles with cost visibility when restricted mode is on. Reads need PM_READ; anything
 * that changes money or budget needs PM_ADMIN, matching the existing budget endpoints.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/budget")
@RequiredArgsConstructor
@Tag(name = "Cost Control", description = "Cost position, cost ledger, budget revisions, transfers and forecasts")
public class CostControlController {

    private final ProjectCostService costService;

    @GetMapping("/cost-summary")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Cost position — original vs revised budget, committed, actual, forecast (EAC), "
            + "pending change-order exposure, contingency, margin")
    public ResponseEntity<ApiResponse<CostSummaryResponse>> summary(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                new CostSummaryResponse(projectId, Instant.now(), costService.summary(tenant(), projectId))));
    }

    // ── Ledger ────────────────────────────────────────────────────────────────

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Cost ledger entries, newest first (optionally for one line; max 500)")
    public ResponseEntity<ApiResponse<List<CostEntryResponse>>> ledger(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID lineId,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                costService.ledger(tenant(), projectId, lineId, limit).stream()
                        .map(e -> CostEntryResponse.of(e, false, false)).toList()));
    }

    @PostMapping("/ledger")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Post a commitment or actual cost. Amount is signed. Send an idempotencyKey to make retries safe.")
    public ResponseEntity<ApiResponse<CostEntryResponse>> post(
            @PathVariable UUID projectId, @Valid @RequestBody PostCostEntryRequest req) {
        String sourceType = blank(req.sourceType()) ? "MANUAL" : req.sourceType().trim();
        String sourceRef  = blank(req.sourceRef())
                ? "MANUAL-" + UUID.randomUUID().toString().substring(0, 8) : req.sourceRef().trim();

        PostedEntry p = costService.post(tenant(), projectId, new CostPosting(
                req.budgetLineId(), req.type(), req.amount(), sourceType, sourceRef,
                req.description(), req.entryDate(), req.idempotencyKey(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));

        CostEntryResponse body = CostEntryResponse.of(p.entry(), p.duplicate(), p.lineOverBudget());
        String msg = p.duplicate() ? "Already posted — original entry returned"
                : p.lineOverBudget() ? "Posted — this line is now over budget" : "Posted";
        return ResponseEntity.status(p.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(msg, body));
    }

    @PostMapping("/ledger/{entryId}/reverse")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Reverse a ledger entry with a new offsetting entry (an entry can be reversed once)")
    public ResponseEntity<ApiResponse<CostEntryResponse>> reverse(
            @PathVariable UUID projectId, @PathVariable UUID entryId,
            @Valid @RequestBody ReverseCostEntryRequest req) {
        PostedEntry p = costService.reverse(tenant(), projectId, entryId, req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Entry reversed",
                CostEntryResponse.of(p.entry(), false, p.lineOverBudget())));
    }

    // ── Revisions / transfers / forecast ──────────────────────────────────────

    @GetMapping("/revisions")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "History of every budget change — newest first")
    public ResponseEntity<ApiResponse<List<BudgetRevisionResponse>>> revisions(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                costService.revisions(tenant(), projectId).stream().map(BudgetRevisionResponse::of).toList()));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Move budget between lines. Total budget is unchanged; only unspent, uncommitted budget can move.")
    public ResponseEntity<ApiResponse<BudgetTransferResponse>> transfer(
            @PathVariable UUID projectId, @Valid @RequestBody BudgetTransferRequest req) {
        TransferResult r = costService.transfer(tenant(), projectId, req.fromLineId(), req.toLineId(),
                req.amount(), req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Budget transferred",
                new BudgetTransferResponse(r.transferId(),
                        BudgetLineResponse.of(r.from()), BudgetLineResponse.of(r.to()))));
    }

    @PutMapping("/lines/{lineId}/forecast")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Set (or clear, with null) the estimate of additional cost still to come on a line")
    public ResponseEntity<ApiResponse<BudgetLineResponse>> setForecast(
            @PathVariable UUID projectId, @PathVariable UUID lineId,
            @Valid @RequestBody SetForecastRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Forecast updated",
                BudgetLineResponse.of(costService.setForecast(tenant(), projectId, lineId, req.forecastToComplete()))));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}