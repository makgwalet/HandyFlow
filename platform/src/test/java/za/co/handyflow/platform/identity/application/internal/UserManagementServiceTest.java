package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.handyflow.platform.billing.application.SubscriptionQueryFacade;
import za.co.handyflow.platform.identity.domain.model.*;
import za.co.handyflow.platform.identity.domain.repository.*;
import za.co.handyflow.platform.identity.dto.request.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.JwtService;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.identity.dto.response.InvitationResponse;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for UserManagementService — no Spring context, matching
 * this codebase's own established convention (see ClinicServiceTest).
 * <p>
 * Written as part of the identity module modernization pass. Focused
 * specifically on the business rules that pass and the ones this pass
 * added/fixed:
 * <ul>
 *   <li>the seat-limit enforcement on inviteUser() (pre-existing, given
 *       regression coverage here since it had none),</li>
 *   <li>the new "last remaining admin" guard on updateUser() and
 *       setUserStatus() (new in this pass — see UserManagementService's
 *       own comments on both methods),</li>
 *   <li>the new pending-only guard on cancelInvitation(),</li>
 *   <li>changePassword()'s existing current-password and
 *       same-password rules.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock UserInvitationRepository invitationRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock JwtService jwtService;
    @Mock DisposableEmailChecker disposableEmailChecker;
    @Mock SubscriptionQueryFacade subscriptionQueryFacade;
    @Mock JdbcTemplate jdbc;
    @Mock AccessAuditService auditService;
    @Mock RefreshTokenSecuritySweep securitySweep;

    @Mock za.co.handyflow.platform.shared.TokenVersions tokenVersions;
    @InjectMocks UserManagementService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT = TenantId.of(TENANT_UUID);

    @org.junit.jupiter.api.AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    /** Makes the current request look like a user holding exactly these authorities. */
    private static void callerHolds(String... authorities) {
        var list = java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("caller", null, list));
    }

    private static Role roleWith(String name, Permission... perms) {
        Role r = role(name);
        for (Permission p : perms) r.addPermission(p);
        return r;
    }

    private static Permission perm(String name) {
        return Permission.of(name, name);
    }

    private static Role role(String name) {
        return Role.create(TENANT, name, name + " role");
    }

    private static User activeUser(Role... roles) {
        User u = User.create(TENANT, "user" + UUID.randomUUID() + "@zeta.co.za", "hashed", "Jane", "Dlamini");
        for (Role r : roles) u.assignRole(r);
        return u;
    }

    @Nested
    @DisplayName("inviteUser — seat limit enforcement")
    class InviteUserSeatLimit {

        @Test
        @DisplayName("blocks inviting once active users + pending invites reach the plan's max")
        void blocksAtSeatLimit() {
            InviteUserRequest req = new InviteUserRequest("new@zeta.co.za", "New", "Hire", null, null, null);
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(false);
            when(subscriptionQueryFacade.getMaxUsers(TENANT)).thenReturn(2);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(TENANT_UUID))).thenReturn(1);
            UserInvitation pending = UserInvitation.create(TENANT, "pending@zeta.co.za", "P", "L", null, null, role("EMPLOYEE"), UUID.randomUUID());
            when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(pending));

            assertThatThrownBy(() -> service.inviteUser(TENANT, UUID.randomUUID(), req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("Your plan allows up to 2 users");

            verify(invitationRepository, never()).save(any());
            verify(emailService, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("unlimited plan (maxUsers = -1) skips the seat check entirely")
        void unlimitedPlanSkipsCheck() {
            InviteUserRequest req = new InviteUserRequest("new@zeta.co.za", "New", "Hire", null, null, null);
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(false);
            when(subscriptionQueryFacade.getMaxUsers(TENANT)).thenReturn(-1);
            when(userRepository.existsByTenantIdAndEmail(TENANT, "new@zeta.co.za")).thenReturn(false);
            when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
            when(roleRepository.findByNameAndTenantId("EMPLOYEE", TENANT)).thenReturn(Optional.of(role("EMPLOYEE")));

            service.inviteUser(TENANT, UUID.randomUUID(), req);

            verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any());
            verify(invitationRepository).save(any());
        }

        @Test
        @DisplayName("rejects a disposable-domain invite email")
        void rejectsDisposableEmail() {
            InviteUserRequest req = new InviteUserRequest("throwaway@mailinator.com", "New", "Hire", null, null, null);
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(true);

            assertThatThrownBy(() -> service.inviteUser(TENANT, UUID.randomUUID(), req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disposable");

            verifyNoInteractions(subscriptionQueryFacade, invitationRepository);
        }
    }

    @Nested
    @DisplayName("updateUser — last-admin protection")
    class UpdateUserLastAdmin {

        @Test
        @DisplayName("blocks reassigning the tenant's only active admin to a non-admin role")
        void blocksDemotingLastAdmin() {
            UUID userId = UUID.randomUUID();
            User onlyAdmin = activeUser(role("ADMIN"));
            Role employeeRole = role("EMPLOYEE");
            UUID employeeRoleId = employeeRole.getId();

            when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.of(onlyAdmin));
            when(roleRepository.findByIdAndTenantId(employeeRoleId, TENANT)).thenReturn(Optional.of(employeeRole));
            when(userRepository.countByTenantIdAndRoleNameAndStatus(TENANT, "ADMIN", User.UserStatus.ACTIVE))
                    .thenReturn(1);

            UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, null, employeeRoleId);

            assertThatThrownBy(() -> service.updateUser(TENANT, userId, req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("only remaining administrator");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows reassigning an admin's role when another active admin still exists")
        void allowsDemotionWhenAnotherAdminExists() {
            UUID userId = UUID.randomUUID();
            User admin = activeUser(role("ADMIN"));
            Role employeeRole = role("EMPLOYEE");
            UUID employeeRoleId = employeeRole.getId();

            when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.of(admin));
            when(roleRepository.findByIdAndTenantId(employeeRoleId, TENANT)).thenReturn(Optional.of(employeeRole));
            when(userRepository.countByTenantIdAndRoleNameAndStatus(TENANT, "ADMIN", User.UserStatus.ACTIVE))
                    .thenReturn(2);

            UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, null, employeeRoleId);
            service.updateUser(TENANT, userId, req);

            verify(userRepository).save(admin);
            assertThat(admin.getRoles()).extracting(Role::getName).containsExactly("EMPLOYEE");
        }

        @Test
        @DisplayName("allows editing a non-role field without touching the last-admin check at all")
        void allowsProfileEditWithoutRoleChange() {
            UUID userId = UUID.randomUUID();
            User onlyAdmin = activeUser(role("ADMIN"));

            when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.of(onlyAdmin));

            UpdateUserRequest req = new UpdateUserRequest("Jane", "Updated", null, null, null, null);
            service.updateUser(TENANT, userId, req);

            verify(userRepository).save(onlyAdmin);
            // roleId was null — the last-admin guard must never even
            // query the count when no role change was requested.
            verifyNoInteractions(roleRepository);
        }
    }

    @Nested
    @DisplayName("setUserStatus — last-admin protection")
    class SetUserStatusLastAdmin {

        @Test
        @DisplayName("blocks self-deactivation regardless of admin status (pre-existing rule)")
        void blocksSelfDeactivation() {
            UUID userId = UUID.randomUUID();

            assertThatThrownBy(() -> service.setUserStatus(TENANT, userId, false, userId))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("cannot deactivate your own account");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("blocks deactivating a DIFFERENT user who is the tenant's only active admin")
        void blocksDeactivatingLastAdmin() {
            UUID targetId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User onlyAdmin = activeUser(role("ADMIN"));

            when(userRepository.findByIdAndTenantId(targetId, TENANT)).thenReturn(Optional.of(onlyAdmin));
            when(userRepository.countByTenantIdAndRoleNameAndStatus(TENANT, "ADMIN", User.UserStatus.ACTIVE))
                    .thenReturn(1);

            assertThatThrownBy(() -> service.setUserStatus(TENANT, targetId, false, requesterId))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("only remaining administrator");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows deactivating an admin when another active admin remains")
        void allowsDeactivatingAdminWhenAnotherExists() {
            UUID targetId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User admin = activeUser(role("ADMIN"));

            when(userRepository.findByIdAndTenantId(targetId, TENANT)).thenReturn(Optional.of(admin));
            when(userRepository.countByTenantIdAndRoleNameAndStatus(TENANT, "ADMIN", User.UserStatus.ACTIVE))
                    .thenReturn(2);

            service.setUserStatus(TENANT, targetId, false, requesterId);

            assertThat(admin.isActive()).isFalse();
            verify(userRepository).save(admin);
        }

        @Test
        @DisplayName("reactivation never triggers the last-admin check")
        void reactivationSkipsLastAdminCheck() {
            UUID targetId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            User inactiveEmployee = activeUser(role("EMPLOYEE"));
            inactiveEmployee.deactivate();

            when(userRepository.findByIdAndTenantId(targetId, TENANT)).thenReturn(Optional.of(inactiveEmployee));

            service.setUserStatus(TENANT, targetId, true, requesterId);

            assertThat(inactiveEmployee.isActive()).isTrue();
            verifyNoInteractions(subscriptionQueryFacade); // sanity: no billing lookup on this path either
        }
    }

    @Nested
    @DisplayName("cancelInvitation")
    class CancelInvitation {

        @Test
        @DisplayName("rejects cancelling an invitation that is no longer pending")
        void rejectsCancellingNonPending() {
            UUID invitationId = UUID.randomUUID();
            UserInvitation accepted = UserInvitation.create(TENANT, "a@zeta.co.za", "A", "B", null, null, role("EMPLOYEE"), UUID.randomUUID());
            accepted.accept();
            when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(accepted));

            assertThatThrownBy(() -> service.cancelInvitation(TENANT, invitationId))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("Only pending invitations can be cancelled");

            verify(invitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancels a genuinely pending invitation")
        void cancelsPendingInvitation() {
            UUID invitationId = UUID.randomUUID();
            UserInvitation pending = UserInvitation.create(TENANT, "a@zeta.co.za", "A", "B", null, null, role("EMPLOYEE"), UUID.randomUUID());
            when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(pending));

            service.cancelInvitation(TENANT, invitationId);

            verify(invitationRepository).save(pending);
            assertThat(pending.isPending()).isFalse();
        }
    }

    @Nested
    @DisplayName("grantor ceiling (IAM Phase 0.1)")
    class GrantorCeiling {

        @Test
        @DisplayName("a caller with only USER_INVITE cannot invite into a role that carries more")
        void inviteAboveAuthority() {
            callerHolds("USER_INVITE");
            Role admin = roleWith("ADMIN", perm("USER_INVITE"), perm("ROLE_MANAGE"), perm("PAYROLL_APPROVE"));
            InviteUserRequest req = new InviteUserRequest("new@zeta.co.za", "New", "Hire", null, null, admin.getId());
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(false);
            when(subscriptionQueryFacade.getMaxUsers(TENANT)).thenReturn(-1);
            when(userRepository.existsByTenantIdAndEmail(TENANT, "new@zeta.co.za")).thenReturn(false);
            when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
            when(roleRepository.findByIdAndTenantId(admin.getId(), TENANT)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.inviteUser(TENANT, UUID.randomUUID(), req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("ROLE_MANAGE")
                    .hasMessageContaining("PAYROLL_APPROVE");

            verify(invitationRepository, never()).save(any());
            verify(emailService, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("a caller can invite into a role whose permissions they all hold")
        void inviteWithinAuthority() {
            callerHolds("USER_INVITE", "CUSTOMER_READ");
            Role sales = roleWith("SALES", perm("CUSTOMER_READ"));
            InviteUserRequest req = new InviteUserRequest("new@zeta.co.za", "New", "Hire", null, null, sales.getId());
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(false);
            when(subscriptionQueryFacade.getMaxUsers(TENANT)).thenReturn(-1);
            when(userRepository.existsByTenantIdAndEmail(TENANT, "new@zeta.co.za")).thenReturn(false);
            when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
            when(roleRepository.findByIdAndTenantId(sales.getId(), TENANT)).thenReturn(Optional.of(sales));

            service.inviteUser(TENANT, UUID.randomUUID(), req);

            verify(invitationRepository).save(any());
            verify(auditService).record(eq(TENANT), eq("INVITATION_CREATED"), eq("INVITATION"), any(), eq("new@zeta.co.za"), any(), any());
        }

        @Test
        @DisplayName("nobody can change their own role")
        void cannotChangeOwnRole() {
            UUID me = UUID.randomUUID();
            TenantContext.setUserId(me.toString());
            callerHolds("USER_UPDATE");
            Role employee = role("EMPLOYEE");
            when(userRepository.findByIdAndTenantId(me, TENANT)).thenReturn(Optional.of(activeUser(role("EMPLOYEE"))));
            when(roleRepository.findByIdAndTenantId(employee.getId(), TENANT)).thenReturn(Optional.of(employee));

            UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, null, employee.getId());

            assertThatThrownBy(() -> service.updateUser(TENANT, me, req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("cannot change your own role");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("cannot assign a role above own authority, even to someone else")
        void cannotAssignRoleAboveAuthority() {
            callerHolds("USER_UPDATE");
            UUID target = UUID.randomUUID();
            Role admin = roleWith("ADMIN", perm("ROLE_MANAGE"));
            when(userRepository.findByIdAndTenantId(target, TENANT)).thenReturn(Optional.of(activeUser(role("EMPLOYEE"))));
            when(roleRepository.findByIdAndTenantId(admin.getId(), TENANT)).thenReturn(Optional.of(admin));

            UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, null, admin.getId());

            assertThatThrownBy(() -> service.updateUser(TENANT, target, req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("ROLE_MANAGE");
        }

        @Test
        @DisplayName("cannot change the role of someone who holds more access than the caller")
        void cannotEditMorePowerfulUser() {
            callerHolds("USER_UPDATE");
            UUID target = UUID.randomUUID();
            User boss = activeUser(roleWith("ADMIN", perm("ROLE_MANAGE")));
            Role employee = role("EMPLOYEE");
            when(userRepository.findByIdAndTenantId(target, TENANT)).thenReturn(Optional.of(boss));
            when(roleRepository.findByIdAndTenantId(employee.getId(), TENANT)).thenReturn(Optional.of(employee));

            UpdateUserRequest req = new UpdateUserRequest(null, null, null, null, null, employee.getId());

            assertThatThrownBy(() -> service.updateUser(TENANT, target, req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("more access than you");
        }

        @Test
        @DisplayName("createRole refuses permissions the creator lacks, and records an audit event when allowed")
        void createRoleCeiling() {
            callerHolds("ROLE_MANAGE", "CUSTOMER_READ");
            Permission customerRead = perm("CUSTOMER_READ");
            Permission payrollApprove = perm("PAYROLL_APPROVE");
            when(roleRepository.existsByNameAndTenantId(anyString(), eq(TENANT))).thenReturn(false);
            when(permissionRepository.findAllById(any())).thenReturn(List.of(customerRead, payrollApprove));

            assertThatThrownBy(() -> service.createRole(TENANT,
                    new CreateRoleRequest("CLERK", "d", Set.of(customerRead.getId(), payrollApprove.getId()))))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("PAYROLL_APPROVE");
            verify(roleRepository, never()).save(any());

            when(permissionRepository.findAllById(any())).thenReturn(List.of(customerRead));
            service.createRole(TENANT, new CreateRoleRequest("CLERK", "d", Set.of(customerRead.getId())));
            verify(roleRepository).save(any());
            verify(auditService).record(eq(TENANT), eq("ROLE_CREATED"), eq("ROLE"), any(), eq("CLERK"), any(), any());
        }

        @Test
        @DisplayName("updateRolePermissions: adding a permission the caller lacks is refused; removing is allowed")
        void updateRoleCeiling() {
            callerHolds("ROLE_MANAGE", "CUSTOMER_READ");
            Permission customerRead = perm("CUSTOMER_READ");
            Permission payrollApprove = perm("PAYROLL_APPROVE");
            Role clerk = roleWith("CLERK", customerRead);
            when(roleRepository.findByIdAndTenantId(clerk.getId(), TENANT)).thenReturn(Optional.of(clerk));
            when(permissionRepository.findAllById(any())).thenReturn(List.of(customerRead, payrollApprove));

            assertThatThrownBy(() -> service.updateRolePermissions(TENANT, clerk.getId(),
                    Set.of(customerRead.getId(), payrollApprove.getId())))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("PAYROLL_APPROVE");

            // removing everything: no lookup is needed, so nothing is stubbed here
            service.updateRolePermissions(TENANT, clerk.getId(), Set.of());
            assertThat(clerk.getPermissions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("sessions and audit on status change (IAM Phase 0.3 / 0.4)")
    class StatusChangeSessions {

        @Test
        @DisplayName("deactivating a user revokes their sessions and writes an audit event")
        void deactivateRevokesSessions() {
            UUID targetId = UUID.randomUUID();
            User employee = activeUser(role("EMPLOYEE"));
            when(userRepository.findByIdAndTenantId(targetId, TENANT)).thenReturn(Optional.of(employee));

            service.setUserStatus(TENANT, targetId, false, UUID.randomUUID());

            verify(securitySweep).revokeAllActiveSessionsForUser(employee.getId());
            verify(auditService).record(eq(TENANT), eq("USER_DEACTIVATED"), eq("USER"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("reactivating does not touch sessions")
        void reactivateLeavesSessionsAlone() {
            UUID targetId = UUID.randomUUID();
            User employee = activeUser(role("EMPLOYEE"));
            employee.deactivate();
            when(userRepository.findByIdAndTenantId(targetId, TENANT)).thenReturn(Optional.of(employee));

            service.setUserStatus(TENANT, targetId, true, UUID.randomUUID());

            verifyNoInteractions(securitySweep);
            verify(auditService).record(eq(TENANT), eq("USER_REACTIVATED"), eq("USER"), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("invitations: expiry, resend, email failure (IAM Phase 0.5)")
    class InvitationLifecycle {

        @Test
        @DisplayName("an invitation past its expiry no longer holds a seat")
        void expiredInviteFreesSeat() throws Exception {
            InviteUserRequest req = new InviteUserRequest("new@zeta.co.za", "New", "Hire", null, null, null);
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(false);
            when(subscriptionQueryFacade.getMaxUsers(TENANT)).thenReturn(2);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(TENANT_UUID))).thenReturn(1);
            UserInvitation stale = UserInvitation.create(TENANT, "old@zeta.co.za", "O", "L", null, null, role("EMPLOYEE"), UUID.randomUUID());
            var f = UserInvitation.class.getDeclaredField("expiresAt");
            f.setAccessible(true);
            f.set(stale, java.time.Instant.now().minusSeconds(60));
            when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(stale));
            when(userRepository.existsByTenantIdAndEmail(TENANT, "new@zeta.co.za")).thenReturn(false);
            when(roleRepository.findByNameAndTenantId("EMPLOYEE", TENANT)).thenReturn(Optional.of(role("EMPLOYEE")));

            service.inviteUser(TENANT, UUID.randomUUID(), req);   // 1 active + 0 live pending < 2

            verify(invitationRepository).save(argThat(i -> "new@zeta.co.za".equals(i.getEmail())));
        }

        @Test
        @DisplayName("the invitation response says when the email could not be sent")
        void reportsEmailFailure() {
            InviteUserRequest req = new InviteUserRequest("new@zeta.co.za", "New", "Hire", null, null, null);
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(false);
            when(subscriptionQueryFacade.getMaxUsers(TENANT)).thenReturn(-1);
            when(userRepository.existsByTenantIdAndEmail(TENANT, "new@zeta.co.za")).thenReturn(false);
            when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
            when(roleRepository.findByNameAndTenantId("EMPLOYEE", TENANT)).thenReturn(Optional.of(role("EMPLOYEE")));
            doThrow(new RuntimeException("smtp down")).when(emailService).send(anyString(), anyString(), anyString());

            InvitationResponse res = service.inviteUser(TENANT, UUID.randomUUID(), req);

            assertThat(res.emailSent()).isFalse();
        }

        @Test
        @DisplayName("resend rotates the token so the old link stops working")
        void resendRotatesToken() {
            UserInvitation inv = UserInvitation.create(TENANT, "a@zeta.co.za", "A", "B", null, null, role("EMPLOYEE"), UUID.randomUUID());
            String oldHash = inv.getTokenHash();
            when(invitationRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
            when(userRepository.existsByTenantIdAndEmail(TENANT, "a@zeta.co.za")).thenReturn(false);

            InvitationResponse res = service.resendInvitation(TENANT, inv.getId());

            assertThat(inv.getTokenHash()).isNotEqualTo(oldHash);
            assertThat(res.emailSent()).isTrue();
            verify(emailService).send(eq("a@zeta.co.za"), anyString(), argThat(html -> html.contains(inv.getRawToken())));
        }

        @Test
        @DisplayName("an accepted invitation cannot be re-sent")
        void acceptedCannotBeResent() {
            UserInvitation inv = UserInvitation.create(TENANT, "a@zeta.co.za", "A", "B", null, null, role("EMPLOYEE"), UUID.randomUUID());
            inv.accept();
            when(invitationRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
            when(userRepository.existsByTenantIdAndEmail(TENANT, "a@zeta.co.za")).thenReturn(false);

            assertThatThrownBy(() -> service.resendInvitation(TENANT, inv.getId()))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("pending or expired");
        }
    }

    @Nested
    @DisplayName("directory visibility (IAM Phase 0.6)")
    class DirectoryVisibility {

        @Test
        @DisplayName("without ROLE_READ the user list carries no permission sets")
        void listHidesPermissions() {
            callerHolds("USER_READ");
            User u = activeUser(roleWith("ADMIN", perm("ROLE_MANAGE")));
            when(userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(TENANT)).thenReturn(List.of(u));

            assertThat(service.getUsers(TENANT).get(0).permissions()).isEmpty();
        }

        @Test
        @DisplayName("with ROLE_READ the list carries permission sets")
        void listShowsPermissionsToRoleReaders() {
            callerHolds("USER_READ", "ROLE_READ");
            User u = activeUser(roleWith("ADMIN", perm("ROLE_MANAGE")));
            when(userRepository.findByTenantIdOrderByLastNameAscFirstNameAsc(TENANT)).thenReturn(List.of(u));

            assertThat(service.getUsers(TENANT).get(0).permissions()).containsExactly("ROLE_MANAGE");
        }

        @Test
        @DisplayName("a user always sees their own permissions")
        void ownPermissionsVisible() {
            UUID me = UUID.randomUUID();
            TenantContext.setUserId(me.toString());
            callerHolds("USER_READ");
            User u = activeUser(roleWith("EMPLOYEE", perm("CUSTOMER_READ")));
            when(userRepository.findByIdAndTenantId(me, TENANT)).thenReturn(Optional.of(u));

            assertThat(service.getUser(TENANT, me).permissions()).containsExactly("CUSTOMER_READ");
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("rejects an incorrect current password")
        void rejectsWrongCurrentPassword() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(role("EMPLOYEE"));
            when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            ChangePasswordRequest req = new ChangePasswordRequest("wrong", "NewSecurePass1");

            assertThatThrownBy(() -> service.changePassword(userId, TENANT, req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("Current password is incorrect");
        }

        @Test
        @DisplayName("rejects a new password identical to the current one")
        void rejectsSameNewPassword() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(role("EMPLOYEE"));
            when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("SamePass1", "hashed")).thenReturn(true);

            ChangePasswordRequest req = new ChangePasswordRequest("SamePass1", "SamePass1");

            assertThatThrownBy(() -> service.changePassword(userId, TENANT, req))
                    .isInstanceOf(HandyFlowException.class)
                    .hasMessageContaining("must be different");
        }

        @Test
        @DisplayName("accepts a valid password change")
        void acceptsValidChange() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(role("EMPLOYEE"));
            when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1", "hashed")).thenReturn(true);
            when(passwordEncoder.encode("NewSecurePass1")).thenReturn("new-hashed");

            ChangePasswordRequest req = new ChangePasswordRequest("OldPass1", "NewSecurePass1");
            service.changePassword(userId, TENANT, req);

            assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
            verify(userRepository).save(user);
        }
    }
}
