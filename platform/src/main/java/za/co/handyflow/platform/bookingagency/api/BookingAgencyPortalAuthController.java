package za.co.handyflow.platform.bookingagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.bookingagency.application.internal.BookingAgencyPortalAuthService;
import za.co.handyflow.platform.bookingagency.dto.PortalAuthResponse;
import za.co.handyflow.platform.bookingagency.dto.PortalLoginRequest;
import za.co.handyflow.platform.bookingagency.dto.PortalRegisterRequest;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * NOTE: /register and /login live under /auth/** — needs adding to
 * SecurityConfig's permitAll() list AND RateLimitFilter's LIMITS array,
 * same as every other portal auth controller this session. Uses
 * /auth/accept-invite from the start, matching the resolved convention
 * (Section 56), not the shape that needed correcting later.
 */
@RestController
@RequestMapping("/api/v1/booking-agency/portal")
@RequiredArgsConstructor
@Tag(name = "Booking Agency Client Portal", description = "Client-facing portal authentication")
public class BookingAgencyPortalAuthController {

    private final BookingAgencyPortalAuthService portalAuthService;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the booking agency client portal")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }
}