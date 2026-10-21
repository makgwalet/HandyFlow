package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.HrAccess;
import za.co.handyflow.platform.hr.application.internal.OvertimeService;
import za.co.handyflow.platform.hr.dto.OvertimeDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Overtime claims worked out from attendance, and their approval. */
@RestController
@RequestMapping("/api/v1/hr/overtime")
@RequiredArgsConstructor
@Tag(name = "Overtime", description = "Claims made from the attendance record, approved before payroll pays them")
public class OvertimeController {

    private static final String SIGNED_IN = "isAuthenticated()";
    private static final String DECIDE = "hasAnyAuthority('HR_MANAGE','HR_OVERTIME_APPROVE')";

    private final OvertimeService service;

    @PostMapping("/request")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Make claims for the overtime in a period. Safe to repeat: waiting claims follow the record, approved ones are left alone")
    public ResponseEntity<ApiResponse<RequestResult>> request(@RequestBody RangeRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.request(TenantContext.getTenantIdAsObject(), req, user(auth), HrAccess.held(auth))));
    }

    @GetMapping
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Claims, optionally by status. Team managers see their own team")
    public ResponseEntity<ApiResponse<List<Claim>>> list(@RequestParam(required = false) String status, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, HrAccess.held(auth), user(auth))));
    }

    @GetMapping("/mine")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<List<Claim>>> mine(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.mine(TenantContext.getTenantIdAsObject(), user(auth), from, to)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(DECIDE)
    public ResponseEntity<ApiResponse<Claim>> approve(@PathVariable UUID id, @RequestBody(required = false) NoteRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Overtime approved",
                service.approve(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.note(), user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(DECIDE)
    @Operation(summary = "Reject a waiting claim, or take back an approved one that no pay run has taken")
    public ResponseEntity<ApiResponse<Claim>> reject(@PathVariable UUID id, @RequestBody NoteRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Overtime rejected",
                service.reject(TenantContext.getTenantIdAsObject(), id, req.note(), user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<Claim>> withdraw(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Claim withdrawn",
                service.withdraw(TenantContext.getTenantIdAsObject(), id, user(auth), HrAccess.held(auth))));
    }

    private static UUID user(Authentication auth) {
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}
