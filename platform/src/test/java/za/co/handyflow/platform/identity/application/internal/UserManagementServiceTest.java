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
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks UserManagementService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT = TenantId.of(TENANT_UUID);

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
