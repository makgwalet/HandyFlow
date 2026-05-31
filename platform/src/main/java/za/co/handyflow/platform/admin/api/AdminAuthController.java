package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminAuthService;
import za.co.handyflow.platform.admin.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Admin Auth", description = "Superadmin authentication with TOTP 2FA")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    @Operation(summary = "Step 1 — Password login. Returns partialToken if TOTP enabled, or TOTP_SETUP_REQUIRED if first login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.login(req, getIp(http))));
    }

    @PostMapping("/verify-totp")
    @Operation(summary = "Step 2 — Submit 6-digit TOTP code. Returns full 30-minute JWT on success")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> verifyTotp(
            @Valid @RequestBody AdminTotpRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.verifyTotp(req, getIp(http))));
    }

    @PostMapping("/totp/setup")
    @Operation(summary = "Generate TOTP secret for a new admin — call after password login when state=TOTP_SETUP_REQUIRED")
    public ResponseEntity<ApiResponse<AdminTotpSetupResponse>> setupTotp(
            @RequestParam UUID adminId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.setupTotp(adminId)));
    }

    @PostMapping("/totp/confirm")
    @Operation(summary = "Confirm TOTP setup by entering first code from authenticator app")
    public ResponseEntity<ApiResponse<Void>> confirmTotp(
            @RequestParam UUID adminId,
            @Valid @RequestBody AdminTotpConfirmRequest req) {
        adminAuthService.confirmTotpSetup(adminId, req.code());
        return ResponseEntity.ok(ApiResponse.success(
                "TOTP enabled. Use Google Authenticator for all future logins.", null));
    }

    @PostMapping("/impersonate")
    @Operation(summary = "Generate a read-only 15-minute impersonation token for a tenant — logged in audit trail")
    public ResponseEntity<ApiResponse<String>> impersonate(
            @Valid @RequestBody ImpersonateRequest req,
            HttpServletRequest http) {
        // Resolve tenant ID from slug
        UUID tenantId = resolveTenantId(req.tenantSlug());
        UUID adminId  = TenantContext.getCurrentUserId();
        String adminEmail = extractAdminEmailFromContext();

        String token = adminAuthService.impersonateTenant(
                adminId, adminEmail, tenantId,
                req.tenantSlug(), req.reason(), getIp(http));

        return ResponseEntity.ok(ApiResponse.success(
                "Impersonation token generated — expires in 15 minutes. Read-only.", token));
    }

    private String getIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }

    private UUID resolveTenantId(String slug) {
        // Resolved via AdminService — placeholder, wired through context
        return UUID.randomUUID(); // TODO: wire properly in AdminService
    }

    private String extractAdminEmailFromContext() {
        return "admin@handyflow.co.za"; // TODO: extract from JWT claims
    }
}
