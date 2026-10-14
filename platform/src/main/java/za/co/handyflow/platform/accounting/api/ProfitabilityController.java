package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.ProfitabilityService;
import za.co.handyflow.platform.accounting.application.internal.TagRuleService;
import za.co.handyflow.platform.accounting.dto.ProfitabilityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Profitability by cost centre and the rules that tag journal lines automatically (finance F28). */
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@Tag(name = "Profitability")
public class ProfitabilityController {

    private final ProfitabilityService profitability;
    private final TagRuleService rules;

    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) { return ResponseEntity.ok(ApiResponse.success(msg, data)); }

    @GetMapping("/profitability")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Income, costs and profit for each cost centre of a kind (site, vehicle, person, project, branch...)")
    public ResponseEntity<ApiResponse<View>> view(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "false") boolean rollUp) {
        return ok("Success", profitability.view(tenant(), from, to, kind, rollUp));
    }

    @GetMapping("/profitability/{centreId}/trend")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<Trend>> trend(@PathVariable UUID centreId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok("Success", profitability.trend(tenant(), centreId, from, to));
    }

    @GetMapping("/tag-rules")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<Rule>>> list() { return ok("Success", rules.list(tenant())); }

    @PostMapping("/tag-rules")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Rule>> create(@RequestBody RuleSave req) { return ok("Saved", rules.create(tenant(), req, TenantContext.getCurrentUserId())); }

    @PostMapping("/tag-rules/{id}/active")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<List<Rule>>> active(@PathVariable UUID id, @RequestBody SetActive req) { return ok("Saved", rules.setActive(tenant(), id, req.active())); }

    @DeleteMapping("/tag-rules/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<List<Rule>>> delete(@PathVariable UUID id) { return ok("Removed", rules.delete(tenant(), id)); }

    @PostMapping("/tag-rules/apply")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    @Operation(summary = "Preview (apply=false) or apply the rules to untagged income and expense lines already posted in a period. Changes no amounts; every line tagged is logged")
    public ResponseEntity<ApiResponse<ApplyResult>> apply(@RequestBody ApplyRequest req) { return ok(req.apply() ? "Applied" : "Preview", rules.retro(tenant(), req, TenantContext.getCurrentUserId())); }
}
