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
        return userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(tenantId)
                .stream().map(this::toUserResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(TenantId tenantId, UUID userId) {
        return toUserResponse(findUserInTenant(tenantId, userId));
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

            user.clearRoles();
            user.assignRole(role);
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
        int maxUsers = subscriptionQueryFacade.getMaxUsers(tenantId);
        if (maxUsers >= 0) { // -1 = unlimited, matching the same sentinel convention used elsewhere in this schema
            Integer activeUserCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId.getValue());
            long pendingInviteCount = invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                    .filter(UserInvitation::isPending)
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

        UserInvitation inv = UserInvitation.create(
                tenantId, email, req.firstName(), req.lastName(),
                req.jobTitle(), req.department(), role, invitedBy);
        invitationRepository.save(inv);

        // FIX: was bare inline HTML with no company name, inviter name,
        // or role name — role.getName() was sitting right here unused.
        // invitedByName and companyName need their own lookups since
        // this method only had UUIDs/TenantId to work with, not the
        // actual names.
        String link    = "https://app.handyflow.co.za/invite/accept?token=" + inv.getToken();
        String subject = "You've been invited to HandyFlow";
        String invitedByName = userRepository.findByIdAndTenantId(invitedBy, tenantId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("A team member");
        String companyName = tenantRepository.findById(tenantId.getValue())
                .map(Tenant::getName)
                .orElse("your company");
        String html = EmailTemplates.userInvitation(
                req.firstName(), invitedByName, companyName, role.getName(), link);

        try {
            emailService.send(email, subject, html);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", email, e.getMessage());
        }

        log.info("Invited {} to tenant={}", email, tenantId);
        return toInvitationResponse(inv);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getInvitations(TenantId tenantId) {
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
    }

    /** Validate a token for the accept-invite page — returns invitation details if valid. */
    @Transactional(readOnly = true)
    public InvitationResponse validateInvitationToken(String token) {
        UserInvitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> bad("Invalid or expired invitation link"));
        if (!inv.isPending())
            throw bad("This invitation has already been used or cancelled");
        if (inv.isExpired())
            throw bad("This invitation link has expired. Ask your admin to send a new one.");
        return toInvitationResponse(inv);
    }

    @Transactional
    public AuthResponse acceptInvitation(AcceptInvitationRequest req) {
        UserInvitation inv = invitationRepository.findByToken(req.token())
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
        String token = jwtService.generateToken(
                user.getId(),
                inv.getTenantId().getValue(),   // UUID from TenantId
                user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getPermissionNames()
        );
        return new AuthResponse(
                token, "Bearer", jwtService.getExpirationSeconds(),
                user.getId(),
                inv.getTenantId().getValue(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPermissionNames(),
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
        if (req.permissionIds() != null && !req.permissionIds().isEmpty())
            permissionRepository.findAllById(req.permissionIds()).forEach(role::addPermission);

        roleRepository.save(role);
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

        role.clearPermissions();
        if (permissionIds != null && !permissionIds.isEmpty())
            permissionRepository.findAllById(permissionIds).forEach(role::addPermission);

        roleRepository.save(role);
        log.info("Updated permissions for role={} in tenant={}", roleId, tenantId);
        return toRoleResponse(role,
                userRepository.countByTenantIdAndRoleName(tenantId, role.getName()));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> new PermissionResponse(p.getId(), p.getName(), p.getDescription()))
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

    private UserResponse toUserResponse(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(),
                u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getJobTitle(), u.getDepartment(),
                u.getStatus().name(),
                u.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                u.getPermissionNames(),
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
        return new InvitationResponse(
                inv.getId(), inv.getEmail(),
                inv.getFirstName(), inv.getLastName(),
                inv.getJobTitle(), inv.getDepartment(),
                inv.getRole().getName(),
                inv.getStatus().name(),
                inv.getExpiresAt(), inv.getCreatedAt()
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