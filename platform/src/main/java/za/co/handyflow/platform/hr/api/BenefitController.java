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
import za.co.handyflow.platform.hr.application.internal.BenefitService;
import za.co.handyflow.platform.hr.application.internal.PayrollAccess;
import za.co.handyflow.platform.hr.dto.BenefitDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Taxable employer-paid benefits: the employer's medical and retirement contributions and other fringe benefits. */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "Employee benefits", description = "Taxable benefits that raise the income PAYE is worked out on")
public class BenefitController {

    private final BenefitService service;

    @GetMapping("/employees/{id}/benefits")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "An employee's taxable benefits, newest first")
    public ResponseEntity<ApiResponse<List<View>>> list(@PathVariable UUID id, Authentication authentication) {
        if (!PayrollAccess.canReview(authentication)) throw new AccessDeniedException("You may not see payroll benefits.");
        return ResponseEntity.ok(ApiResponse.success(service.forEmployee(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/benefits")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a taxable benefit. It applies to pay runs whose period ends within its dates")
    public ResponseEntity<ApiResponse<View>> add(@PathVariable UUID id, @RequestBody AddRequest req, Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not change payroll benefits.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Benefit added",
                service.add(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @PostMapping("/benefits/{id}/end")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "End a taxable benefit on a date. Pay runs for later periods no longer include it")
    public ResponseEntity<ApiResponse<View>> end(@PathVariable UUID id, @RequestBody EndRequest req, Authentication authentication, Principal principal) {
        if (!PayrollAccess.canPrepare(authentication)) throw new AccessDeniedException("You may not change payroll benefits.");
        return ResponseEntity.ok(ApiResponse.success("Benefit ended", service.end(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    private static UUID user(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (Exception e) { return null; }
    }
}
