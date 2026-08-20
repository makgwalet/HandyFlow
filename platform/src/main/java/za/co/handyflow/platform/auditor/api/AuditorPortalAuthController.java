package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditorPortalAuthService;
import za.co.handyflow.platform.auditor.dto.AuditorPortalAuthResponse;
import za.co.handyflow.platform.shared.ApiResponse;

@RestController
@RequestMapping("/api/v1/auditor/portal/auth")
@RequiredArgsConstructor
@Tag(name = "Auditor Portal Auth", description = "External auditor portal login/registration")
public class AuditorPortalAuthController {

    private final AuditorPortalAuthService authService;

    public record RegisterRequest(String inviteToken, String password, String fullName) {}
    public record LoginRequest(String email, String password) {}

    @PostMapping("/register")
    @Operation(summary = "Register via an auditor invite link")
    public ResponseEntity<ApiResponse<AuditorPortalAuthResponse>> register(@RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/login")
    @Operation(summary = "Auditor portal login")
    public ResponseEntity<ApiResponse<AuditorPortalAuthResponse>> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(req.email(), req.password())));
    }
}