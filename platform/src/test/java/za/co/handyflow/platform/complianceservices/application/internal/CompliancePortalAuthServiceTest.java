package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePortalGrant;
import za.co.handyflow.platform.complianceservices.domain.repository.CompliancePortalGrantRepository;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.AuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompliancePortalAuthServiceTest {

    @Mock PortalUserRepository portalUsers;
    @Mock CompliancePortalGrantRepository grants;
    @Mock PasswordEncoder encoder;
    @Mock PortalJwtService jwt;

    private CompliancePortalAuthService service;
    private CompliancePortalGrant grant;

    @BeforeEach
    void setUp() {
        service = new CompliancePortalAuthService(portalUsers, grants, encoder, jwt);
        grant = CompliancePortalGrant.invite(UUID.randomUUID(), UUID.randomUUID(), "jo@acme.co.za", UUID.randomUUID(), Instant.now());
        lenient().when(grants.findByInviteToken(grant.getInviteToken())).thenReturn(Optional.of(grant));
        lenient().when(encoder.encode(any())).thenReturn("hash");
        lenient().when(portalUsers.save(any(PortalUser.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(jwt.generateToken(any(), any())).thenReturn("jwt");
    }

    @Test
    @DisplayName("A new person registers through the invite and the grant becomes active")
    void registers() {
        lenient().when(portalUsers.findByEmail("jo@acme.co.za")).thenReturn(Optional.empty());
        AuthResponse r = service.register(grant.getInviteToken(), "a-long-password", " Jo Smith ");
        assertThat(r.token()).isEqualTo("jwt");
        assertThat(r.fullName()).isEqualTo("Jo Smith");
        assertThat(grant.isActive()).isTrue();
    }

    @Test
    @DisplayName("A person who already has a login must give their existing password")
    void existingNeedsPassword() {
        PortalUser existing = PortalUser.create("jo@acme.co.za", "hash", "Jo");
        lenient().when(portalUsers.findByEmail("jo@acme.co.za")).thenReturn(Optional.of(existing));
        lenient().when(encoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.register(grant.getInviteToken(), "wrong", "Jo")).isInstanceOf(HandyFlowException.class).hasMessageContaining("existing password");
        assertThat(grant.isActive()).isFalse();
        verify(grants, never()).save(any());
    }

    @Test
    @DisplayName("An unknown or used invite is refused")
    void badInvite() {
        lenient().when(grants.findByInviteToken("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register("nope", "a-long-password", "Jo")).isInstanceOf(HandyFlowException.class).hasMessageContaining("invalid");
    }

    @Test
    @DisplayName("A wrong password or a disabled account cannot sign in")
    void login() {
        PortalUser u = PortalUser.create("jo@acme.co.za", "hash", "Jo");
        lenient().when(portalUsers.findByEmail("jo@acme.co.za")).thenReturn(Optional.of(u));
        lenient().when(encoder.matches("wrong", "hash")).thenReturn(false);
        lenient().when(encoder.matches("right-password", "hash")).thenReturn(true);
        assertThatThrownBy(() -> service.login("jo@acme.co.za", "wrong")).isInstanceOf(HandyFlowException.class);
        assertThat(service.login(" JO@acme.co.za ", "right-password").token()).isEqualTo("jwt");
        u.deactivate();
        assertThatThrownBy(() -> service.login("jo@acme.co.za", "right-password")).isInstanceOf(HandyFlowException.class).hasMessageContaining("disabled");
    }
}
