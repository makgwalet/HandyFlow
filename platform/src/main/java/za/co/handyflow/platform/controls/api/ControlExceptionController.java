package za.co.handyflow.platform.controls.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.controls.application.internal.ControlExceptionManagementService;
import za.co.handyflow.platform.controls.dto.ControlExceptionDetail;
import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The shared "needs attention" board itself — one screen, every open
 * exception, from every module that's adopted ControlExceptionFacade.
 * No FeatureGuard.requireModule() check, same reasoning as
 * EvidenceController: this is foundational shared infrastructure any
 * tenant can see, not a subscribable module of its own.
 */
@RestController
@RequestMapping("/api/v1/control-exceptions")
@RequiredArgsConstructor
@Tag(name = "Control Exceptions", description = "Shared cross-module \"needs attention\" board")
public class ControlExceptionController {

    private final ControlExceptionFacade controlExceptionFacade;
    private final ControlExceptionManagementService management;

    @GetMapping
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_READ')")
    @Operation(summary = "List every open exception across every module")
    public ResponseEntity<ApiResponse<List<ControlExceptionResponse>>> listOpen() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(controlExceptionFacade.listOpen(tenantId)));
    }

    // ---- Management (A15): rate, assign, respond, resolve, follow up ----

    @GetMapping("/detailed")
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_READ')")
    @Operation(summary = "Exceptions with rating, owner, response and follow-up (scope: open, follow-ups, all)")
    public ResponseEntity<ApiResponse<List<ControlExceptionDetail>>> detailed(@RequestParam(defaultValue = "open") String scope) {
        return ResponseEntity.ok(ApiResponse.success(management.list(TenantContext.getTenantIdAsObject(), scope, LocalDate.now())));
    }

    public record RateRequest(String rating, String rationale) {}
    public record AssignRequest(UUID ownerId, LocalDate targetDate) {}
    public record RespondRequest(String response) {}
    public record ResolveExceptionRequest(String resolutionNotes, LocalDate followUpDate) {}
    public record FollowUpRequest(String outcome, String note) {}

    @PostMapping("/{id}/rate")
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_RESOLVE')")
    @Operation(summary = "Rate the risk of an exception")
    public ResponseEntity<ApiResponse<ControlExceptionDetail>> rate(@PathVariable UUID id, @RequestBody RateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(management.rate(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.rating(), req.rationale(), LocalDate.now())));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_RESOLVE')")
    @Operation(summary = "Give an exception an owner and a target date")
    public ResponseEntity<ApiResponse<ControlExceptionDetail>> assign(@PathVariable UUID id, @RequestBody AssignRequest req) {
        return ResponseEntity.ok(ApiResponse.success(management.assign(TenantContext.getTenantIdAsObject(), id,
                req.ownerId(), req.targetDate(), LocalDate.now())));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_RESOLVE')")
    @Operation(summary = "Record management's response")
    public ResponseEntity<ApiResponse<ControlExceptionDetail>> respond(@PathVariable UUID id, @RequestBody RespondRequest req) {
        return ResponseEntity.ok(ApiResponse.success(management.respond(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.response(), LocalDate.now())));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_RESOLVE')")
    @Operation(summary = "Resolve an exception (must be rated; high and critical also need a response, evidence, a different resolver and a follow-up date)")
    public ResponseEntity<ApiResponse<ControlExceptionDetail>> resolve(@PathVariable UUID id, @RequestBody ResolveExceptionRequest req) {
        return ResponseEntity.ok(ApiResponse.success(management.resolve(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.resolutionNotes(), req.followUpDate(), LocalDate.now())));
    }

    @PostMapping("/{id}/follow-up")
    @PreAuthorize("hasAuthority('CONTROL_EXCEPTION_RESOLVE')")
    @Operation(summary = "Record the follow-up check: HOLDING or RECURRED (reopens)")
    public ResponseEntity<ApiResponse<ControlExceptionDetail>> followUp(@PathVariable UUID id, @RequestBody FollowUpRequest req) {
        return ResponseEntity.ok(ApiResponse.success(management.completeFollowUp(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.outcome(), req.note(), LocalDate.now())));
    }
}
