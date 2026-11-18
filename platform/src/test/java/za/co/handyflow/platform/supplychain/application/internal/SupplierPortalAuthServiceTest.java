package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierPortalGrant;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierPortalGrantRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupplierPortalAuthServiceTest {

    private final PortalUserRepository users = mock(PortalUserRepository.class);
    private final ScSupplierPortalGrantRepository grants = mock(ScSupplierPortalGrantRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PortalJwtService jwt = mock(PortalJwtService.class);
    private final SupplierPortalAuthService service = new SupplierPortalAuthService(users, grants, encoder, jwt);

    private ScSupplierPortalGrant invite(String email) {
        ScSupplierPortalGrant g = ScSupplierPortalGrant.createInvite(UUID.randomUUID(), UUID.randomUUID(), email, UUID.randomUUID());
        when(grants.findByInviteToken(g.getInviteToken())).thenReturn(Optional.of(g));
        return g;
    }

    @Test
    void anInviteIsSingleUseAndExpires() {
        ScSupplierPortalGrant g = invite("Sam@Acme.test");
        assertThat(g.getInviteEmail()).isEqualTo("sam@acme.test");
        assertThat(g.isInviteValid()).isTrue();
        String token = g.getInviteToken();
        g.acceptInvite(UUID.randomUUID());
        assertThat(g.isActive()).isTrue();
        assertThat(g.getInviteToken()).isNull();
        assertThat(g.isInviteValid()).isFalse();
        assertThatThrownBy(() -> g.acceptInvite(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(token).hasSizeGreaterThan(30);
    }

    @Test
    void anExpiredInviteCannotRegisterAnAccount() {
        ScSupplierPortalGrant g = invite("sam@acme.test");
        ReflectionTestUtils.setField(g, "inviteTokenExpiresAt", Instant.now().minusSeconds(5));
        assertThatThrownBy(() -> service.register(g.getInviteToken(), "a-long-password", "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("expired");
        verify(users, never()).save(any());
    }

    @Test
    void anUnknownTokenAndAWeakPasswordAreRefused() {
        assertThatThrownBy(() -> service.register("nope", "a-long-password", "Sam")).hasMessageContaining("invalid");
        ScSupplierPortalGrant g = invite("sam@acme.test");
        assertThatThrownBy(() -> service.register(g.getInviteToken(), "short", "Sam")).hasMessageContaining("at least 10");
    }

    @Test
    void anExistingAccountMustLogInInsteadOfRegistering() {
        ScSupplierPortalGrant g = invite("sam@acme.test");
        when(users.findByEmail("sam@acme.test")).thenReturn(Optional.of(mock(PortalUser.class)));
        assertThatThrownBy(() -> service.register(g.getInviteToken(), "a-long-password", "Sam")).hasMessageContaining("log in");
    }

    @Test
    void anInviteForAnotherEmailCannotBeAcceptedAtLogin() {
        ScSupplierPortalGrant g = invite("boss@acme.test");
        PortalUser u = mock(PortalUser.class);
        when(u.isActive()).thenReturn(true);
        when(u.getEmail()).thenReturn("sam@acme.test");
        when(u.getPasswordHash()).thenReturn("h");
        when(users.findByEmail("sam@acme.test")).thenReturn(Optional.of(u));
        when(encoder.matches("pw", "h")).thenReturn(true);
        assertThatThrownBy(() -> service.login("sam@acme.test", "pw", g.getInviteToken()))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("different email");
        assertThat(g.isActive()).isFalse();
    }

    @Test
    void aWrongPasswordGivesTheSameAnswerAsAnUnknownEmail() {
        PortalUser u = mock(PortalUser.class);
        when(u.isActive()).thenReturn(true);
        when(u.getPasswordHash()).thenReturn("h");
        when(users.findByEmail("sam@acme.test")).thenReturn(Optional.of(u));
        when(encoder.matches("bad", "h")).thenReturn(false);
        assertThatThrownBy(() -> service.login("sam@acme.test", "bad", null)).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.login("ghost@acme.test", "bad", null)).hasMessage("Invalid email or password");
    }
}
