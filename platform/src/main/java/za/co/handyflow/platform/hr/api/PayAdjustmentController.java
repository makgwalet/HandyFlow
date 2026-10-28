package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.LeaveAdvanceService;
import za.co.handyflow.platform.hr.application.internal.NoticePayService;
import za.co.handyflow.platform.hr.application.internal.PayCorrectionService;
import za.co.handyflow.platform.hr.application.internal.PayAdjustmentService;
import za.co.handyflow.platform.hr.application.internal.PayrollAccess;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Bonuses and back pay, taken by the next pay run. */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "Pay adjustments", description = "Bonuses and back pay paid through a pay run")
public class PayAdjustmentController {

    private final PayAdjustmentService service;
    private final LeaveAdvanceService leaveAdvance;
    private final NoticePayService noticePay;
    private final PayCorrectionService corrections;

    @GetMapping("/pay-adjustments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bonuses and back pay, newest first. Filter by status OPEN or CANCELLED")
    public ResponseEntity<ApiResponse<List<View>>> list(@RequestParam(required = false) String status, Authentication authentication) {
        if (!PayrollAccess.canReview(authentication)) throw new AccessDeniedException("You may not see bonuses and back pay.");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/employees/{id}/pay-adjustments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "One employee's bonuses and back pay")
    public ResponseEntity<ApiResponse<List<View>>> forEmployee(@PathVariable UUID id, Authentication authentication) {
        if (!PayrollAccess.canReview(authentication)) throw new AccessDeniedException("You may not see bonuses and back pay.");
        return ResponseEntity.ok(ApiResponse.success(service.forEmployee(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/pay-adjustments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Enter a bonus or back pay. The next pay run whose period ends on or after the date pays it")
    public ResponseEntity<ApiResponse<View>> add(@PathVariable UUID id, @RequestBody AddRequest req, Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not enter bonuses or back pay.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment entered",
                service.add(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @GetMapping("/employees/{id}/leave-advance")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Annual leave a terminated employee took before earning it, what it comes to, and whether a recovery is already entered")
    public ResponseEntity<ApiResponse<LeaveAdvanceService.Position>> leaveAdvance(@PathVariable UUID id, Authentication authentication) {
        if (!PayrollAccess.canReview(authentication)) throw new AccessDeniedException("You may not see this.");
        return ResponseEntity.ok(ApiResponse.success(leaveAdvance.position(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/leave-advance/recover")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Take leave taken in advance back from final pay, on the strength of the employee's written agreement, named and dated")
    public ResponseEntity<ApiResponse<View>> recoverLeaveAdvance(@PathVariable UUID id, @RequestBody LeaveAdvanceService.RecoverRequest req,
                                                                 Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not enter a recovery.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Recovery entered",
                leaveAdvance.recover(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @GetMapping("/employees/{id}/notice-pay")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Notice required, given and short for someone let go with notice, the pay owed for the days short, and whether it is already entered")
    public ResponseEntity<ApiResponse<NoticePayService.Position>> noticePay(@PathVariable UUID id, Authentication authentication) {
        if (!PayrollAccess.canReview(authentication)) throw new AccessDeniedException("You may not see this.");
        return ResponseEntity.ok(ApiResponse.success(noticePay.position(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/notice-pay/enter")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Enter pay in place of notice for the final run; the amount defaults to the pay owed for the days short")
    public ResponseEntity<ApiResponse<View>> enterNoticePay(@PathVariable UUID id, @RequestBody NoticePayService.EnterRequest req,
                                                            Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not enter a payment.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Notice pay entered",
                noticePay.enter(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @GetMapping("/payslips/{id}/corrections")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Corrections entered against an issued payslip")
    public ResponseEntity<ApiResponse<List<View>>> corrections(@PathVariable UUID id, Authentication authentication) {
        if (!PayrollAccess.canReview(authentication)) throw new AccessDeniedException("You may not see this.");
        return ResponseEntity.ok(ApiResponse.success(corrections.forPayslip(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/payslips/{id}/corrections")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Correct an issued payslip: back pay for money owed, or a recovery of money paid by mistake. The payslip itself is not changed")
    public ResponseEntity<ApiResponse<View>> correct(@PathVariable UUID id, @RequestBody PayCorrectionService.EnterRequest req,
                                                     Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not enter a correction.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Correction entered",
                corrections.enter(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @PostMapping("/pay-adjustments/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel a payment no pay run has taken yet")
    public ResponseEntity<ApiResponse<View>> cancel(@PathVariable UUID id, @RequestBody CancelRequest req, Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not cancel bonuses or back pay.");
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    private static UUID user(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (Exception e) { return null; }
    }
}
