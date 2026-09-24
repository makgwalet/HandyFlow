package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendVerificationEmailActionTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private EmailService emailService;

    private ResendVerificationEmailAction action() {
        return new ResendVerificationEmailAction(userRepository, emailVerificationService, emailService);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ADMIN = UUID.randomUUID();

    @Test
    @DisplayName("key() is the stable identifier used for dispatch")
    void key_isStable() {
        assertThat(action().key()).isEqualTo("RESEND_VERIFICATION_EMAIL");
    }

    @Test
    @DisplayName("a null targetId fails clearly rather than attempting a lookup with no id")
    void nullTargetId_fails() {
        var result = action().execute(TENANT, null, Map.of(), ADMIN);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("targetId");
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("a targetId that doesn't resolve to a user for this tenant fails clearly")
    void unknownUser_fails() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdAndTenantId(userId, TENANT)).thenReturn(Optional.empty());

        var result = action().execute(TENANT, userId, Map.of(), ADMIN);

        assertThat(result.success()).isFalse();
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("an already-verified user is a no-op, not an error that also sends an email")
    void alreadyVerified_noOp() {
        User user = User.create(TENANT, "jane@example.com", "hash", "Jane", "Doe");
        user.verifyEmail();
        when(userRepository.findByIdAndTenantId(user.getId(), TENANT)).thenReturn(Optional.of(user));

        var result = action().execute(TENANT, user.getId(), Map.of(), ADMIN);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("already verified");
        verify(emailVerificationService, never()).createToken(any(), any());
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("an unverified user gets a fresh token and an email, and the action reports success")
    void unverifiedUser_resendsSuccessfully() {
        User user = User.create(TENANT, "jane@example.com", "hash", "Jane", "Doe");
        when(userRepository.findByIdAndTenantId(user.getId(), TENANT)).thenReturn(Optional.of(user));
        when(emailVerificationService.createToken(user.getId(), TENANT.getValue())).thenReturn("fresh-token-123");

        var result = action().execute(TENANT, user.getId(), Map.of(), ADMIN);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("jane@example.com");
        verify(emailService).send(eq("jane@example.com"), eq("Verify your email address"), any());
    }
}
