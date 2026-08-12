package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.identity.application.internal.RefreshTokenService;
import za.co.handyflow.platform.identity.application.internal.UserManagementService;
import za.co.handyflow.platform.identity.dto.request.*;
import za.co.handyflow.platform.identity.dto.response.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.RefreshCookieUtil;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * FIX (HandyFlow BOS Discovery doc, Section 60/66): acceptInvitation() —
 * the third path (alongside AuthController's register()/login()) that
 * produces a fresh login — was the only one that never called
 * issueRefreshCookie(). A user who joined via invite got a working
 * access token but no refresh cookie to silently renew it once that
 * token expired, unlike everyone who registered or logged in directly.
 * refreshTokenService/refreshCookieUtil and the three private helpers
 * below are duplicated from AuthController.java verbatim rather than
 * extracted into a shared component — both controllers already live in
 * this same `identity` module, so extraction is a legitimate future
 * cleanup if you'd rather not carry two copies of ~15 lines, but it's
 * more churn than this specific bug warrants fixing on its own.
 */
@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Manage users, roles and invitations within a tenant")
public class UserController {

    private final UserManagementService userService;
    // NEW: see class-level Javadoc — only used by acceptInvitation()'s
    // issueRefreshCookie() call below.
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieUtil   refreshCookieUtil;

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all users in the tenant")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers() {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUsers(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get a single user")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUser(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update user profile or role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(ApiResponse.success("User updated",
                userService.updateUser(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE')")
    @Operation(summary = "Deactivate a user — they can no longer log in")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("User deactivated",
                userService.setUserStatus(
                        TenantContext.getTenantIdAsObject(), id,
                        false, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/users/{id}/reactivate")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE')")
    @Operation(summary = "Reactivate a previously deactivated user")
    public ResponseEntity<ApiResponse<UserResponse>> reactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("User reactivated",
                userService.setUserStatus(
                        TenantContext.getTenantIdAsObject(), id,
                        true, TenantContext.getCurrentUserId())));
    }

    // ── Invitations ───────────────────────────────────────────────────────────

    @PostMapping("/users/invite")
    @PreAuthorize("hasAuthority('USER_INVITE')")
    @Operation(summary = "Invite a new user — sends an email with a sign-up link")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteUser(
            @Valid @RequestBody InviteUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invitation sent to " + req.email(),
                        userService.inviteUser(
                                TenantContext.getTenantIdAsObject(),
                                TenantContext.getCurrentUserId(),
                                req)));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('USER_INVITE')")
    @Operation(summary = "List all invitations for this tenant")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getInvitations() {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getInvitations(TenantContext.getTenantIdAsObject())));
    }

    @DeleteMapping("/invitations/{id}")
    @PreAuthorize("hasAuthority('USER_INVITE')")
    @Operation(summary = "Cancel a pending invitation")
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(@PathVariable UUID id) {
        userService.cancelInvitation(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Invitation cancelled", null));
    }

    // ── Accept invitation (public — no auth required) ──────────────────────────

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept an invitation and create account — no auth required")
    public ResponseEntity<ApiResponse<AuthResponse>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse auth = userService.acceptInvitation(req);
        issueRefreshCookie(auth, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", auth));
    }

    @GetMapping("/invitations/validate/{token}")
    @Operation(summary = "Validate invitation token before showing the accept form")
    public ResponseEntity<ApiResponse<InvitationResponse>> validateToken(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.validateInvitationToken(token)));
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "List all roles in the tenant with permission assignments")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getRoles(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Create a new role with optional initial permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created",
                        userService.createRole(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Replace all permissions on a role")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRolePermissions(
            @PathVariable UUID id,
            @RequestBody Set<UUID> permissionIds) {
        return ResponseEntity.ok(ApiResponse.success("Role permissions updated",
                userService.updateRolePermissions(
                        TenantContext.getTenantIdAsObject(), id, permissionIds)));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "List all system permissions — used to build role assignment UI")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllPermissions()));
    }

    // ── B2: Update own profile ────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUser(
                        TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId())));
    }

    @PutMapping("/me")
    @Operation(summary = "Update own profile — firstName, lastName, phone, jobTitle, department")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                userService.updateOwnProfile(
                        TenantContext.getCurrentUserId(),
                        TenantContext.getTenantIdAsObject(),
                        req)));
    }

    // ── B3: Change own password ───────────────────────────────────────────────

    @PostMapping("/me/password")
    @Operation(summary = "Change own password — requires current password for verification")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(
                TenantContext.getCurrentUserId(),
                TenantContext.getTenantIdAsObject(),
                req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // ── Internal ─────────────────────────────────────────────────────────────
    // Exact copies of AuthController's own three private helpers — see
    // class-level Javadoc for why these are duplicated rather than shared.

    private void issueRefreshCookie(AuthResponse response, HttpServletRequest request,
                                    HttpServletResponse httpResponse) {
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(
                response.userId(), response.tenantId(),
                extractDeviceFingerprint(request), extractIp(request),
                request.getHeader("User-Agent"));
        ResponseCookie cookie = refreshCookieUtil.build(issued.rawToken(), issued.expiresAt());
        httpResponse.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractDeviceFingerprint(HttpServletRequest request) {
        return request.getHeader("X-Device-Fingerprint");
    }
}