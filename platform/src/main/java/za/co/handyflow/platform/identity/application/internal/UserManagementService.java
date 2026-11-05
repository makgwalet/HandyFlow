package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.*;
import za.co.handyflow.platform.identity.domain.repository.*;
import za.co.handyflow.platform.identity.dto.request.*;
import za.co.handyflow.platform.identity.dto.response.*;
import za.co.handyflow.platform.shared.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository           userRepository;
    private final RoleRepository           roleRepository;
    private final PermissionRepository     permissionRepository;
    private final UserInvitationRepository invitationRepository;
    private final TenantRepository         tenantRepository;
    private final PasswordEncoder          passwordEncoder;
    private final EmailService             emailService;
    private final JwtService               jwtService;
    private final DisposableEmailChecker disposableEmailChecker;
    /**
     * Same accepted coupling as AuthService.subscriptionQueryFacade — see
     * that field's Javadoc for the full explanation. Used here for
     * getMaxUsers() during user invitation (seat-limit enforcement), which
     * has the same "entitlement is part of auth" justification.
     */
    private final za.co.handyflow.platform.billing.application.SubscriptionQueryFacade subscriptionQueryFacade;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final AccessAuditService       auditService;
    private final RefreshTokenSecuritySweep securitySweep;
    private final TokenPermissions tokenPermissions;

    /** Base URL of the web app used in emailed links (property app.frontend.url, same as the other modules). */
    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:https://app.handyflow.co.za}")
    private String frontendUrl = "https://app.handyflow.co.za";

    // ── Helper: throw with 400 BAD_REQUEST ───────────────────────────────────
    private static HandyFlowException bad(String message) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }
    private static HandyFlowException notFound(String message) {
        return new HandyFlowException(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
    private static HandyFlowException forbidden(String message) {
        return new HandyFlowException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    // ── List users ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(TenantId tenantId) {
        boolean showPermissions = GrantCeiling.callerAuthorities().contains("ROLE_READ");
        return userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(tenantId)
                .stream().map(u -> toUserResponse(u, showPermissions)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(TenantId tenantId, UUID userId) {
        // Another person's permission set is access-review data (audit F6): only role readers
        // see it. A user always sees their own.
        boolean own = userId.equals(currentUserIdOrNull());
        boolean showPermissions = own || GrantCeiling.callerAuthorities().contains("ROLE_READ");
        return toUserResponse(findUserInTenant(tenantId, userId), showPermissions);
    }



    // ── Update user ──────────────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(TenantId tenantId, UUID userId, UpdateUserRequest req) {
        User user = findUserInTenant(tenantId, userId);

        if (req.firstName()  != null) user.setFirstName(req.firstName());
        if (req.lastName()   != null) user.setLastName(req.lastName());
        if (req.phone()      != null) user.setPhone(req.phone());
        if (req.jobTitle()   != null) user.setJobTitle(req.jobTitle());
        if (req.department() != null) user.setDepartment(req.department());

        if (req.roleId() != null) {
            Role role = roleRepository.findByIdAndTenantId(req.roleId(), tenantId)
                    .orElseThrow(() -> bad("Role not found"));

            // IAM Phase 0.1: grantor ceiling. Nobody changes their own role, nobody grants a role
            // above their own authority, and nobody edits someone who holds more than they do.
            Set<String> callerAuth = GrantCeiling.callerAuthorities();
            if (userId.equals(currentUserIdOrNull()))
                throw new HandyFlowException("You cannot change your own role. Ask another administrator.",
                        HttpStatus.FORBIDDEN, GrantCeiling.CODE);
            GrantCeiling.assertWithin(callerAuth, permissionNames(role), "assign the role " + role.getName());
            GrantCeiling.assertWithin(callerAuth, user.getPermissionNames(),
                    "change the role of a user who has more access than you");

            // FIX (identity module modernization): nothing previously
            // stopped a tenant's only ADMIN from being reassigned to a
            // non-admin role — including by themselves, since this
            // endpoint has no self-edit restriction the way
            // setUserStatus()'s deactivate path does. That's a genuine
            // lockout: ROLE_MANAGE/USER_UPDATE are both ADMIN-only
            // permissions by default (see RoleService.createDefaultAdminRole()),
            // so a tenant with zero remaining admins has no user left
            // who can grant admin back to anyone, including via this
            // same endpoint. Only blocks the specific case that causes
            // the lockout — moving the LAST active admin OFF the ADMIN
            // role — not admin-to-admin edits or changing a non-admin's
            // role, which are always safe.
            boolean wasActiveAdmin = user.isActive()
                    && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
            boolean stillAdmin = "ADMIN".equals(role.getName());
            if (wasActiveAdmin && !stillAdmin) {
                int activeAdmins = userRepository.countByTenantIdAndRoleNameAndStatus(
                        tenantId, "ADMIN", User.UserStatus.ACTIVE);
                if (activeAdmins <= 1) {
                    throw bad("Cannot change this user's role — they are the only remaining " +
                            "administrator. Promote another user to ADMIN first.");
                }
            }

            Set<String> before = user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
            user.clearRoles();
            user.assignRole(role);
            auditService.record(tenantId, "USER_ROLE_CHANGED", "USER", user.getId(), user.getEmail(),
                    Map.of("roles", before), Map.of("roles", Set.of(role.getName())));
        }

        userRepository.save(user);
        log.info("Updated user={} in tenant={}", userId, tenantId);
        return toUserResponse(user);
    }

    // ── Deactivate / reactivate ──────────────────────────────────────────────

    @Transactional
    public UserResponse setUserStatus(TenantId tenantId, UUID userId,
                                      boolean active, UUID requestingUserId) {
        if (userId.equals(requestingUserId))
            throw bad("You cannot deactivate your own account");

        User user = findUserInTenant(tenantId, userId);

        // FIX (identity module modernization): the self-deactivation
        // check above only stops an admin locking THEMSELVES out — one
        // admin could still deactivate a DIFFERENT admin who happens to
        // be the tenant's last one, with the exact same end result (a
        // tenant with zero active admins, and therefore nobody left
        // with ROLE_MANAGE/USER_DEACTIVATE to undo it). Same guard
        // shape as updateUser()'s new role-reassignment check above;
        // only fires on the deactivate path (active=false) and only
        // when this user is currently an active admin.
        if (!active && user.isActive()
                && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()))) {
            int activeAdmins = userRepository.countByTenantIdAndRoleNameAndStatus(
                    tenantId, "ADMIN", User.UserStatus.ACTIVE);
            if (activeAdmins <= 1) {
                throw bad("Cannot deactivate this user — they are the only remaining " +
                        "administrator. Promote another user to ADMIN first.");
            }
        }

        if (active) user.activate(); else user.deactivate();
        userRepository.save(user);
        auditService.record(tenantId, active ? "USER_REACTIVATED" : "USER_DEACTIVATED", "USER",
                user.getId(), user.getEmail(), null, null);
        if (!active) {
            // IAM Phase 0.4: end the user's sessions now instead of when the refresh token next expires.
            // Their current access token can still work until it expires (at most the access-token lifetime);
            // closing that gap needs the access-version claim planned for Phase 3.
            int revoked = securitySweep.revokeAllActiveSessionsForUser(user.getId());
            log.info("Deactivated user={}: revoked {} active session(s)", userId, revoked);
        }
        log.info("Set user={} active={} in tenant={}", userId, active, tenantId);
        return toUserResponse(user);
    }

    // ── Invitations ──────────────────────────────────────────────────────────

    @Transactional
    public InvitationResponse inviteUser(TenantId tenantId, UUID invitedBy,
                                         InviteUserRequest req) {
        if (disposableEmailChecker.isDisposable(req.email())) {
            throw new IllegalArgumentException(
                    "Please use a permanent email address to register — " +
                            "disposable/temporary email providers are not accepted"
            );
        }

        String email = req.email().toLowerCase().trim();

        // FIX: previously no check existed here at all — confirmed via
        // real testing that plans.max_users, which genuinely exists in
        // the schema, was never actually enforced anywhere. Any tenant
        // could invite unlimited users regardless of what their plan
        // pays for. Counts active users plus pending invitations
        // together — a pending invite that hasn't been accepted yet
        // still represents a seat that's about to be consumed, and
        // ignoring it would let someone send five simultaneous invites
        // to dodge a four-user limit.
        invitationRepository.markExpired(java.time.Instant.now(),
                UserInvitation.InvitationStatus.PENDING, UserInvitation.InvitationStatus.EXPIRED);
        int maxUsers = subscriptionQueryFacade.getMaxUsers(tenantId);
        if (maxUsers >= 0) { // -1 = unlimited, matching the same sentinel convention used elsewhere in this schema
            Integer activeUserCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId.getValue());
            long pendingInviteCount = invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                    .filter(UserInvitation::isLive)   // expired invitations no longer hold a seat (audit F8)
                    .count();
            int currentTotal = (activeUserCount != null ? activeUserCount : 0) + (int) pendingInviteCount;

            if (currentTotal >= maxUsers) {
                // FIX: was HttpStatus.PAYMENT_REQUIRED (402) in an
                // earlier version of this fix. Confirmed via real testing
                // this collides with client.ts's account-lockout
                // interceptor — that logic correctly catches every 402
                // FeatureGuard throws (all genuine account-level
                // lockouts), but has no way to distinguish THIS 402 from
                // those, since it only checks status code. A routine
                // "you've hit your user limit" rejection was redirecting
                // straight to AccountLockedPage showing "your trial has
                // ended" — completely wrong message for what actually
                // happened. 409 is also a better semantic fit on its own
                // merits: this isn't "please pay us", it's "this action
                // conflicts with your current user count" — and it isn't
                // special-cased anywhere in client.ts, so it simply
                // propagates as a normal error for the calling UI to
                // handle inline, the correct behaviour for a
                // validation-style failure, not a full-page lockout.
                throw new HandyFlowException(
                        "Your plan allows up to " + maxUsers + " users. " +
                                "You currently have " + currentTotal + " (active + pending invites). " +
                                "Upgrade your plan to invite more.",
                        org.springframework.http.HttpStatus.CONFLICT, "USER_LIMIT_EXCEEDED");
            }
        }

        if (userRepository.existsByTenantIdAndEmail(tenantId, email))
            throw bad("A user with email " + email + " already exists in this company");

        // Cancel any existing pending invite for this email
        invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(i -> i.getEmail().equalsIgnoreCase(email) && i.isPending())
                .forEach(i -> { i.cancel(); invitationRepository.save(i); });

        Role role = req.roleId() != null
                ? roleRepository.findByIdAndTenantId(req.roleId(), tenantId)
                .orElseThrow(() -> bad("Role not found"))
                : roleRepository.findByNameAndTenantId("EMPLOYEE", tenantId)
                .orElseGet(() -> createDefaultEmployeeRole(tenantId));

        // IAM Phase 0.1: cannot invite someone into a role that carries access the inviter lacks.
        GrantCeiling.assertWithin(GrantCeiling.callerAuthorities(), permissionNames(role),
                "invite someone as " + role.getName());

        UserInvitation inv = UserInvitation.create(
                tenantId, email, req.firstName(), req.lastName(),
                req.jobTitle(), req.department(), role, invitedBy);
        invitationRepository.save(inv);
        auditService.record(tenantId, "INVITATION_CREATED", "INVITATION", inv.getId(), email,
                null, Map.of("role", role.getName()));

        boolean emailSent = sendInvitationEmail(inv, tenantId, invitedBy);

        log.info("Invited {} to tenant={}", email, tenantId);
        return toInvitationResponse(inv, emailSent);
    }

    /**
     * Sends the invitation email. Returns false when sending failed, so the caller can tell the
     * administrator instead of leaving a pending invitation nobody received (audit F9).
     */
    private boolean sendInvitationEmail(UserInvitation inv, TenantId tenantId, UUID invitedBy) {
        String base = frontendUrl == null ? "https://app.handyflow.co.za" : frontendUrl.replaceAll("/+$", "");
        String link = base + "/invite/accept?token=" + inv.getRawToken();
        String invitedByName = userRepository.findByIdAndTenantId(invitedBy, tenantId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("A team member");
        String companyName = tenantRepository.findById(tenantId.getValue())
                .map(Tenant::getName)
                .orElse("your company");
        String html = EmailTemplates.userInvitation(
                inv.getFirstName(), invitedByName, companyName, inv.getRole().getName(), link);
        try {
            emailService.send(inv.getEmail(), "You've been invited to HandyFlow", html);
            return true;
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", inv.getEmail(), e.getMessage());
            return false;
        }
    }

    /** New link and a fresh 72 hours for a pending or expired invitation; the old link stops working. */
    @Transactional
    public InvitationResponse resendInvitation(TenantId tenantId, UUID invitationId) {
        UserInvitation inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId.toString()));
        if (!inv.getTenantId().equals(tenantId))
            throw notFound("Invitation not found");
        if (userRepository.existsByTenantIdAndEmail(tenantId, inv.getEmail()))
            throw bad("A user with email " + inv.getEmail() + " already exists in this company");
        try {
            inv.renew();
        } catch (IllegalStateException e) {
            throw bad("Only pending or expired invitations can be re-sent — this one is "
                    + inv.getStatus().name().toLowerCase() + ".");
        }
        invitationRepository.save(inv);
        UUID actor = currentUserIdOrNull();
        boolean emailSent = sendInvitationEmail(inv, tenantId, actor != null ? actor : inv.getInvitedBy());
        auditService.record(tenantId, "INVITATION_RESENT", "INVITATION", inv.getId(), inv.getEmail(), null, null);
        return toInvitationResponse(inv, emailSent);
    }

    @Transactional
    public List<InvitationResponse> getInvitations(TenantId tenantId) {
        // Report expiry as a status, not just a date (audit F8). Not read-only because it updates rows.
        invitationRepository.markExpired(java.time.Instant.now(),
                UserInvitation.InvitationStatus.PENDING, UserInvitation.InvitationStatus.EXPIRED);
        return invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().map(this::toInvitationResponse).collect(Collectors.toList());
    }

    @Transactional
    public void cancelInvitation(TenantId tenantId, UUID invitationId) {
        UserInvitation inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId.toString()));
        if (!inv.getTenantId().equals(tenantId))
            throw notFound("Invitation not found");
        // FIX (identity module modernization): previously cancelled
        // unconditionally regardless of current status — an already-
        // ACCEPTED invitation could be "cancelled" after the fact with
        // no error, silently rewriting history for a user who already
        // has a live account (TeamTab.tsx only shows the Cancel button
        // for PENDING rows, so this wasn't reachable from that UI, but
        // the endpoint itself had no such guard for any other caller).
        if (!inv.isPending())
            throw bad("Only pending invitations can be cancelled — this one is " +
                    inv.getStatus().name().toLowerCase() + ".");
        inv.cancel();
        invitationRepository.save(inv);
        auditService.record(tenantId, "INVITATION_CANCELLED", "INVITATION", inv.getId(), inv.getEmail(), null, null);
    }

    /** Validate a token for the accept-invite page — returns invitation details if valid. */
    @Transactional(readOnly = true)
    public InvitationResponse validateInvitationToken(String token) {
        UserInvitation inv = invitationRepository.findByTokenHash(UserInvitation.hashToken(token))
                .orElseThrow(() -> bad("Invalid or expired invitation link"));
        if (!inv.isPending())
            throw bad("This invitation has already been used or cancelled");
        if (inv.isExpired())
            throw bad("This invitation link has expired. Ask your admin to send a new one.");
        return toInvitationResponse(inv);
    }

    @Transactional
    public AuthResponse acceptInvitation(AcceptInvitationRequest req) {
        UserInvitation inv = invitationRepository.findByTokenHash(UserInvitation.hashToken(req.token()))
                .orElseThrow(() -> bad("Invalid or expired invitation link"));

        if (!inv.isPending())
            throw bad("This invitation has already been used or cancelled");
        if (inv.isExpired())
            throw bad("This invitation link has expired. Ask your admin to send a new one.");

        User user = User.create(
                inv.getTenantId(),
                inv.getEmail(),
                passwordEncoder.encode(req.password()),
                inv.getFirstName(),
                inv.getLastName()
        );
        if (inv.getJobTitle()   != null) user.setJobTitle(inv.getJobTitle());
        if (inv.getDepartment() != null) user.setDepartment(inv.getDepartment());
        user.assignRole(inv.getRole());
        userRepository.save(user);

        inv.accept();
        invitationRepository.save(inv);
        auditService.recordAs(inv.getTenantId(), user.getId(), user.getFirstName() + " " + user.getLastName(),
                "INVITATION_ACCEPTED", "USER", user.getId(), user.getEmail(),
                null, Map.of("role", inv.getRole().getName()));

        log.info("Invitation accepted: user={} tenant={}", user.getId(), inv.getTenantId());

        // FIX: same two bugs as AuthService.buildAuthResponse() — 86400L
        // hardcoded independent of JwtService's real configured expiry,
        // and subscriptionStatus missing entirely from the record this
        // constructs. Fetches the real Tenant rather than assuming
        // "ACTIVE" — accepting an invitation is normally sent by an
        // already-active tenant, but that's an assumption, not a
        // guarantee (a tenant could theoretically be suspended in the
        // window between an invite being sent and being accepted), and
        // AuthService already proves the real value is one query away.
        Tenant tenant = tenantRepository.findById(inv.getTenantId().getValue())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant", inv.getTenantId().getValue().toString()));

        // Return an AuthResponse so the user is logged in immediately after accepting
        java.util.Set<String> permissions = tokenPermissions.forToken(inv.getTenantId().getValue(), user.getPermissionNames());
        String token = jwtService.generateToken(
                user.getId(),
                inv.getTenantId().getValue(),   // UUID from TenantId
                user.getEmail(),
                user.getFirstName(), user.getLastName(),
                permissions
        );
        return new AuthResponse(
                token, "Bearer", jwtService.getExpirationSeconds(),
                user.getId(),
                inv.getTenantId().getValue(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                permissions,
                tenant.getStatus().name()
        );
    }

    // ── Roles ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles(TenantId tenantId) {
        return roleRepository.findByTenantId(tenantId).stream()
                .map(role -> {
                    int count = userRepository.countByTenantIdAndRoleName(
                            tenantId, role.getName());
                    return toRoleResponse(role, count);
                }).collect(Collectors.toList());
    }

    @Transactional
    public RoleResponse createRole(TenantId tenantId, CreateRoleRequest req) {
        if (roleRepository.existsByNameAndTenantId(req.name().toUpperCase().trim(), tenantId))
            throw bad("A role named '" + req.name() + "' already exists");

        Role role = Role.create(tenantId, req.name(), req.description());
        if (req.permissionIds() != null && !req.permissionIds().isEmpty()) {
            List<Permission> requested = permissionRepository.findAllById(req.permissionIds());
            // IAM Phase 0.1: a role cannot carry access its creator does not hold.
            GrantCeiling.assertWithin(GrantCeiling.callerAuthorities(),
                    requested.stream().map(Permission::getName).collect(Collectors.toSet()),
                    "create this role");
            requested.forEach(role::addPermission);
        }

        roleRepository.save(role);
        auditService.record(tenantId, "ROLE_CREATED", "ROLE", role.getId(), role.getName(), null,
                Map.of("permissions", permissionNames(role)));
        log.info("Created role={} in tenant={}", role.getName(), tenantId);
        return toRoleResponse(role, 0);
    }

    @Transactional
    public RoleResponse updateRolePermissions(TenantId tenantId, UUID roleId,
                                              Set<UUID> permissionIds) {
        Role role = roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if ("ADMIN".equals(role.getName()))
            throw forbidden("The ADMIN role permissions cannot be modified");

        Set<String> before = permissionNames(role);
        List<Permission> requested = (permissionIds != null && !permissionIds.isEmpty())
                ? permissionRepository.findAllById(permissionIds) : List.of();
        Set<String> after = requested.stream().map(Permission::getName).collect(Collectors.toCollection(TreeSet::new));
        // IAM Phase 0.1: only permissions the caller holds can be ADDED. Removing is always allowed.
        Set<String> added = new TreeSet<>(after);
        added.removeAll(before);
        GrantCeiling.assertWithin(GrantCeiling.callerAuthorities(), added, "add these permissions to the role");

        role.clearPermissions();
        requested.forEach(role::addPermission);

        roleRepository.save(role);
        auditService.record(tenantId, "ROLE_PERMISSIONS_CHANGED", "ROLE", role.getId(), role.getName(),
                Map.of("permissions", new TreeSet<>(before)), Map.of("permissions", after));
        log.info("Updated permissions for role={} in tenant={}", roleId, tenantId);
        return toRoleResponse(role,
                userRepository.countByTenantIdAndRoleName(tenantId, role.getName()));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> new PermissionResponse(p.getId(), p.getName(), p.getDescription(), PermissionResponse.groupOf(p.getName())))
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User findUserInTenant(TenantId tenantId, UUID userId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private Role createDefaultEmployeeRole(TenantId tenantId) {
        Role employee = Role.create(tenantId, "EMPLOYEE", "Standard employee access");
        permissionRepository.findByNameIn(Set.of(
                "USER_READ", "CUSTOMER_READ", "INVOICE_READ",
                "BILLING_READ", "REPORT_VIEW"
        )).forEach(employee::addPermission);
        return roleRepository.save(employee);
    }

    private static Set<String> permissionNames(Role r) {
        return r.getPermissions().stream().map(Permission::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    private static UUID currentUserIdOrNull() {
        try { return TenantContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    /** Recent access changes for the tenant (newest first). */
    @Transactional(readOnly = true)
    public List<AccessAuditEventResponse> getAccessAudit(TenantId tenantId, int limit) {
        return auditService.recent(tenantId, limit);
    }

    private UserResponse toUserResponse(User u) {
        return toUserResponse(u, true);
    }

    private UserResponse toUserResponse(User u, boolean includePermissions) {
        return new UserResponse(
                u.getId(), u.getEmail(),
                u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getJobTitle(), u.getDepartment(),
                u.getStatus().name(),
                u.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                includePermissions ? u.getPermissionNames() : Set.of(),
                u.getCreatedAt()
        );
    }

    private RoleResponse toRoleResponse(Role r, int count) {
        return new RoleResponse(
                r.getId(), r.getName(), r.getDescription(),
                r.getPermissions().stream()
                        .map(Permission::getName).collect(Collectors.toSet()),
                count
        );
    }

    private InvitationResponse toInvitationResponse(UserInvitation inv) {
        return toInvitationResponse(inv, null);
    }

    private InvitationResponse toInvitationResponse(UserInvitation inv, Boolean emailSent) {
        return new InvitationResponse(
                inv.getId(), inv.getEmail(),
                inv.getFirstName(), inv.getLastName(),
                inv.getJobTitle(), inv.getDepartment(),
                inv.getRole().getName(),
                inv.getStatus().name(),
                inv.getExpiresAt(), inv.getCreatedAt(), emailSent
        );
    }

    @Transactional
    public UserResponse updateOwnProfile(UUID userId, TenantId tenantId,
                                         UpdateProfileRequest req) {
        User user = findUserInTenant(tenantId, userId);
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        if (req.phone()      != null) user.setPhone(req.phone());
        if (req.jobTitle()   != null) user.setJobTitle(req.jobTitle());
        if (req.department() != null) user.setDepartment(req.department());
        userRepository.save(user);
        log.info("User updated own profile: userId={}", userId);
        return toUserResponse(user);
    }

    // ── B3: Change own password ───────────────────────────────────────────────

    @Transactional
    public void changePassword(UUID userId, TenantId tenantId,
                               ChangePasswordRequest req) {
        User user = findUserInTenant(tenantId, userId);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw bad("Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw bad("New password must be different from your current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        log.info("User changed password: userId={}", userId);
    }
}