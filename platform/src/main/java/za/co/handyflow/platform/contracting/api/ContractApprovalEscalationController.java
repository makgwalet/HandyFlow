package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ApprovalEscalationService;
import za.co.handyflow.platform.contracting.application.internal.ApprovalEscalationService.SettingsInput;
import za.co.handyflow.platform.contracting.domain.model.ContractApproval;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules;
import za.co.handyflow.platform.contracting.dto.ContractApprovalDtos.ApprovalView;
import za.co.handyflow.platform.contracting.dto.ContractEscalationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reminders, escalation, reassignment, time away and response times for approval requests. It follows requests up; it never decides one. */
@RestController
@RequestMapping("/api/v1/contracts/approval-escalation")
@RequiredArgsConstructor
@Tag(name = "Contract approval escalation")
public class ContractApprovalEscalationController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String ACT = "hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String ADMIN = "hasAuthority('CONTRACTS_ADMIN')";

    private final ApprovalEscalationService service;

    @GetMapping("/settings")
    @PreAuthorize(READ)
    @Operation(summary = "Escalation settings (switched off until an administrator turns it on)")
    public ResponseEntity<ApiResponse<SettingsView>> settings() {
        return ResponseEntity.ok(ApiResponse.success("Settings", SettingsView.of(service.settings(TenantContext.getTenantIdAsObject()))));
    }

    @PutMapping("/settings")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Save escalation settings. Turning it on only follows up requests made from then on")
    public ResponseEntity<ApiResponse<SettingsView>> saveSettings(@Valid @RequestBody SettingsRequest q) {
        var input = new SettingsInput(q.enabled(), new EscalationRules.Thresholds(q.reminder1Hours(), q.reminder2Hours(), q.escalateHours()),
                q.clockMode(), q.workStart(), q.workEnd(), q.workDays(), q.skipPublicHolidays(), q.escalationUserIds());
        var s = service.saveSettings(TenantContext.getTenantIdAsObject(), input, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Settings saved", SettingsView.of(s)));
    }

    @GetMapping("/closure-days")
    @PreAuthorize(READ)
    @Operation(summary = "Days the organisation is closed, from today on")
    public ResponseEntity<ApiResponse<List<ClosureView>>> closures() {
        return ResponseEntity.ok(ApiResponse.success("Closure days", service.closures(TenantContext.getTenantIdAsObject()).stream().map(ClosureView::of).toList()));
    }

    @PostMapping("/closure-days")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Mark a day as closed so it does not count towards waiting time")
    public ResponseEntity<ApiResponse<ClosureView>> addClosure(@Valid @RequestBody ClosureRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Closure day added",
                ClosureView.of(service.addClosure(TenantContext.getTenantIdAsObject(), q.day(), q.reason(), TenantContext.getCurrentUserId()))));
    }

    @DeleteMapping("/closure-days/{id}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Remove a closure day that has not happened yet")
    public ResponseEntity<ApiResponse<Void>> removeClosure(@PathVariable UUID id) {
        service.removeClosure(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Closure day removed", null));
    }

    @GetMapping("/absences")
    @PreAuthorize(READ)
    @Operation(summary = "Time away that is under way or coming up")
    public ResponseEntity<ApiResponse<List<AbsenceView>>> absences() {
        return ResponseEntity.ok(ApiResponse.success("Time away", service.absences(TenantContext.getTenantIdAsObject()).stream().map(AbsenceView::of).toList()));
    }

    @PostMapping("/absences")
    @PreAuthorize(ACT)
    @Operation(summary = "Record time away, optionally naming who covers. Your own, or anyone's if you are a contracts administrator")
    public ResponseEntity<ApiResponse<AbsenceView>> addAbsence(@Valid @RequestBody AbsenceRequest q, Authentication auth) {
        var a = service.addAbsence(TenantContext.getTenantIdAsObject(), q.userId(), q.from(), q.to(), q.delegateUserId(), q.reason(),
                TenantContext.getCurrentUserId(), authorities(auth).contains("CONTRACTS_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success("Time away recorded", AbsenceView.of(a)));
    }

    @DeleteMapping("/absences/{id}")
    @PreAuthorize(ACT)
    @Operation(summary = "Cancel time away (your own, or anyone's if you are a contracts administrator)")
    public ResponseEntity<ApiResponse<Void>> cancelAbsence(@PathVariable UUID id, Authentication auth) {
        service.cancelAbsence(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), authorities(auth).contains("CONTRACTS_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success("Time away cancelled", null));
    }

    @PostMapping("/approvals/{id}/reassign")
    @PreAuthorize(ACT)
    @Operation(summary = "Say who is reminded first about a waiting request. Anyone with the right role can still decide it")
    public ResponseEntity<ApiResponse<ApprovalView>> reassign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest q, Authentication auth) {
        Set<String> roles = authorities(auth);
        UUID me = TenantContext.getCurrentUserId();
        ContractApproval a = service.reassign(TenantContext.getTenantIdAsObject(), id, q.userId(), me, roles, q.note());
        return ResponseEntity.ok(ApiResponse.success("Reassigned", ApprovalView.of(a, me, a.getAction().canBeDecidedBy(roles))));
    }

    @GetMapping("/sla")
    @PreAuthorize(READ)
    @Operation(summary = "How long approvals take: average, median, 90th percentile, within target, by action and by approver")
    public ResponseEntity<ApiResponse<SlaView>> sla(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Response times", SlaView.of(service.sla(TenantContext.getTenantIdAsObject(), from, to))));
    }

    private static Set<String> authorities(Authentication auth) {
        return auth == null ? Set.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
