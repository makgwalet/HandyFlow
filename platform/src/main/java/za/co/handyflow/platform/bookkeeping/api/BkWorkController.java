package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkWorkService;
import za.co.handyflow.platform.bookkeeping.dto.BkWorkDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Bank rules, bulk categorising, client queries, work queue and handover to the accountant (finance F19). */
@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - work queue, rules and handover")
public class BkWorkController {

    private static final String READ = "hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')";

    private final BkWorkService service;
    private final FeatureGuard featureGuard;

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T body) {
        return ResponseEntity.ok(ApiResponse.success(message, body));
    }

    @GetMapping("/work-queue")
    @PreAuthorize(READ)
    @Operation(summary = "Per client: lines to categorise, open queries, missing documents, drafts")
    public ResponseEntity<ApiResponse<List<WorkQueueRow>>> workQueue() {
        featureGuard.requireModule("bookkeeping");
        return ok("Success", service.workQueue(TenantContext.getTenantIdAsObject()));
    }

    // rules

    @GetMapping("/clients/{clientId}/bank-rules")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<RuleResponse>>> rules(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookkeeping");
        return ok("Success", service.rules(TenantContext.getTenantIdAsObject(), clientId));
    }

    @PostMapping("/clients/{clientId}/bank-rules")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<RuleResponse>>> addRule(@PathVariable UUID clientId, @RequestBody RuleRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Rule saved", service.addRule(TenantContext.getTenantIdAsObject(), clientId, req, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/clients/{clientId}/bank-rules/{ruleId}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<RuleResponse>>> setActive(@PathVariable UUID clientId, @PathVariable UUID ruleId, @RequestParam boolean active) {
        featureGuard.requireModule("bookkeeping");
        return ok("Updated", service.setRuleActive(TenantContext.getTenantIdAsObject(), clientId, ruleId, active));
    }

    @DeleteMapping("/clients/{clientId}/bank-rules/{ruleId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<RuleResponse>>> deleteRule(@PathVariable UUID clientId, @PathVariable UUID ruleId) {
        featureGuard.requireModule("bookkeeping");
        return ok("Rule removed", service.deleteRule(TenantContext.getTenantIdAsObject(), clientId, ruleId));
    }

    @GetMapping("/clients/{clientId}/bank-transactions/suggestions")
    @PreAuthorize(READ)
    @Operation(summary = "Uncategorised lines with the account the first matching rule would use")
    public ResponseEntity<ApiResponse<List<Suggestion>>> suggestions(@PathVariable UUID clientId, @RequestParam(required = false) UUID bankAccountId) {
        featureGuard.requireModule("bookkeeping");
        return ok("Success", service.suggestions(TenantContext.getTenantIdAsObject(), clientId, bankAccountId));
    }

    @PostMapping("/clients/{clientId}/bank-transactions/categorise")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Book the chosen lines to the chosen accounts, all or nothing (200 at most)")
    public ResponseEntity<ApiResponse<CategoriseResponse>> categorise(@PathVariable UUID clientId, @RequestBody CategoriseRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Categorised", service.categorise(TenantContext.getTenantIdAsObject(), clientId, req, TenantContext.getCurrentUserId()));
    }

    // queries

    @GetMapping("/clients/{clientId}/queries")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<QueryResponse>>> queries(@PathVariable UUID clientId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("bookkeeping");
        return ok("Success", service.queries(TenantContext.getTenantIdAsObject(), clientId, status));
    }

    @PostMapping("/clients/{clientId}/queries")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<QueryResponse>>> ask(@PathVariable UUID clientId, @RequestBody QueryRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Query raised", service.ask(TenantContext.getTenantIdAsObject(), clientId, req, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/clients/{clientId}/queries/{queryId}/answer")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<QueryResponse>>> answer(@PathVariable UUID clientId, @PathVariable UUID queryId, @RequestBody AnswerRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Answer recorded", service.answer(TenantContext.getTenantIdAsObject(), clientId, queryId, req, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/clients/{clientId}/queries/{queryId}/close")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<QueryResponse>>> close(@PathVariable UUID clientId, @PathVariable UUID queryId) {
        featureGuard.requireModule("bookkeeping");
        return ok("Closed", service.close(TenantContext.getTenantIdAsObject(), clientId, queryId));
    }

    // handover

    @GetMapping("/clients/{clientId}/handovers")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<HandoverResponse>>> handovers(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookkeeping");
        return ok("Success", service.handovers(TenantContext.getTenantIdAsObject(), clientId));
    }

    @PostMapping("/clients/{clientId}/handovers")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Hand a period to the accountant; refused while lines are uncategorised or journals are drafts")
    public ResponseEntity<ApiResponse<List<HandoverResponse>>> send(@PathVariable UUID clientId, @RequestBody HandoverRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Handed over", service.sendHandover(TenantContext.getTenantIdAsObject(), clientId, req, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/handovers/{id}/accept")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    @Operation(summary = "Accept a handover; closes the month it runs to")
    public ResponseEntity<ApiResponse<List<HandoverResponse>>> accept(@PathVariable UUID id, @RequestBody(required = false) RespondRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Accepted", service.respond(TenantContext.getTenantIdAsObject(), id, "ACCEPT", req == null ? null : req.note(), TenantContext.getCurrentUserId()));
    }

    @PostMapping("/handovers/{id}/return")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    @Operation(summary = "Return a handover with what needs fixing")
    public ResponseEntity<ApiResponse<List<HandoverResponse>>> returnIt(@PathVariable UUID id, @RequestBody RespondRequest req) {
        featureGuard.requireModule("bookkeeping");
        return ok("Returned", service.respond(TenantContext.getTenantIdAsObject(), id, "RETURN", req.note(), TenantContext.getCurrentUserId()));
    }
}
