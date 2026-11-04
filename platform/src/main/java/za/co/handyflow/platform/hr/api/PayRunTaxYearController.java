package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.PayRunTaxYearService;
import za.co.handyflow.platform.hr.dto.PayRunTaxYearDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/pay-runs/tax-year-labels")
@RequiredArgsConstructor
@Tag(name = "Pay run tax-year labels", description = "Preview and correct pay runs whose tax-year label disagrees with their pay date")
public class PayRunTaxYearController {

    private final PayRunTaxYearService service;

    @GetMapping
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canReview(authentication)")
    @Operation(summary = "Every pay run with its stored and verified tax year. Changes nothing.")
    public ResponseEntity<ApiResponse<Preview>> preview() {
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/apply")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canConfigure(authentication)")
    @Operation(summary = "Correct the label on the chosen runs. Amounts and payslips are never changed.")
    public ResponseEntity<ApiResponse<Applied>> apply(@Valid @RequestBody ApplyRequest req, Principal principal) {
        UUID actor = null;
        try { actor = principal == null ? null : UUID.fromString(principal.getName()); } catch (Exception ignored) { /* keep null */ }
        return ResponseEntity.ok(ApiResponse.success("Tax-year labels corrected",
                service.apply(TenantContext.getTenantIdAsObject(), req.payRunIds(), req.reason(), actor)));
    }
}
