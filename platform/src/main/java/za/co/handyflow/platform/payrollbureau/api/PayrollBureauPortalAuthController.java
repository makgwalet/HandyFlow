// src/main/java/za/co/handyflow/platform/payrollbureau/api/PayrollBureauPortalAuthController.java
package za.co.handyflow.platform.payrollbureau.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.payrollbureau.application.internal.PayrollBureauPortalAuthService;
import za.co.handyflow.platform.payrollbureau.dto.PortalAuthResponse;
import za.co.handyflow.platform.payrollbureau.dto.PortalLoginRequest;
import za.co.handyflow.platform.payrollbureau.dto.PortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * The missing piece — PayrollBureauPortalAuthService already existed,
 * fully implemented, with no controller ever wired to it. Confirmed via
 * a real NoResourceFoundException on /api/v1/payroll-bureau/portal/auth/
 * register: Spring had genuinely nothing mapped at that path. Mirrors
 * RecruitmentAgencyPortalAuthController / BookingAgencyPortalAuthController
 * exactly, including the /auth/accept-invite convention (not the bare
 * /accept-invite shape an earlier draft used elsewhere in this module).
 */
@RestController
@RequestMapping("/api/v1/payroll-bureau/portal")
@RequiredArgsConstructor
@Tag(name = "Payroll Bureau Client Portal", description = "Client-facing portal authentication")
public class PayrollBureauPortalAuthController {

    private final PayrollBureauPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the payroll bureau client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}