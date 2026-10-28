package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.Emp201Service;
import za.co.handyflow.platform.hr.application.internal.PayrollAccess;
import za.co.handyflow.platform.hr.dto.Emp201ActionDtos.PayRequest;
import za.co.handyflow.platform.hr.dto.Emp201ActionDtos.SubmitRequest;
import za.co.handyflow.platform.hr.dto.Emp201Response;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.util.UUID;

/** Recording that an EMP201 was filed with SARS and that it was paid. Nothing is sent to SARS from here. */
@RestController
@RequestMapping("/api/v1/hr/emp201")
@RequiredArgsConstructor
@Tag(name = "EMP201", description = "Filing and payment of the monthly employer declaration")
public class Emp201Controller {

    private final Emp201Service service;

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Record that the EMP201 was filed on SARS eFiling, with the payment reference SARS gave")
    public ResponseEntity<ApiResponse<Emp201Response>> submit(@PathVariable UUID id, @RequestBody SubmitRequest req, Authentication a, Principal p) {
        require(a);
        return ResponseEntity.ok(ApiResponse.success("EMP201 recorded as filed", service.submit(TenantContext.getTenantIdAsObject(), id, req, user(p))));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Record that the filed EMP201 was paid in full, on a date")
    public ResponseEntity<ApiResponse<Emp201Response>> pay(@PathVariable UUID id, @RequestBody PayRequest req, Authentication a, Principal p) {
        require(a);
        return ResponseEntity.ok(ApiResponse.success("EMP201 recorded as paid", service.pay(TenantContext.getTenantIdAsObject(), id, req, user(p))));
    }

    private static void require(Authentication a) {
        if (!PayrollAccess.canFinalise(a)) throw new AccessDeniedException("Only people who may finalise a pay run can record the filing and payment of an EMP201.");
    }

    private static UUID user(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (Exception e) { return null; }
    }
}
