package za.co.handyflow.platform.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.identity.application.IdentityFacade;
import za.co.handyflow.platform.identity.application.internal.RefreshTokenService;
import za.co.handyflow.platform.identity.dto.request.LoginRequest;
import za.co.handyflow.platform.identity.dto.request.MobileLoginRequest;
import za.co.handyflow.platform.identity.dto.request.MobileRefreshRequest;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;
import za.co.handyflow.platform.identity.dto.response.MobileSession;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MobileAuthControllerTest {

    @Mock IdentityFacade identity;
    @Mock RefreshTokenService refresh;
    @Mock HttpServletRequest http;

    private MobileAuthController controller() { return new MobileAuthController(identity, refresh); }
    private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();
    private final Instant exp = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void login_returnsTheRefreshTokenInTheBody_andBindsItToTheDevice() {
        when(identity.login(any())).thenReturn(new AuthResponse("access", "Bearer", 3600, user, tenant, "d@x.co.za", "Sipho", "D", Set.of("FUEL_DRIVER"), "ACTIVE"));
        when(refresh.issue(eq(user), eq(tenant), eq("device-1"), any(), any())).thenReturn(new RefreshTokenService.IssuedToken("raw-refresh", exp));
        when(http.getRemoteAddr()).thenReturn("10.0.0.1");

        MobileSession s = controller().login(new MobileLoginRequest("d@x.co.za", "pw", "acme", "device-1"), http).getBody().getData();

        assertThat(s.accessToken()).isEqualTo("access");
        assertThat(s.refreshToken()).isEqualTo("raw-refresh");
        assertThat(s.refreshTokenExpiresAt()).isEqualTo(exp);
        assertThat(s.permissions()).containsExactly("FUEL_DRIVER");
        assertThat(s.subscriptionStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<LoginRequest> sent = ArgumentCaptor.forClass(LoginRequest.class);
        verify(identity).login(sent.capture());
        assertThat(sent.getValue().tenantSlug()).isEqualTo("acme");
    }

    @Test
    void login_usesTheFirstForwardedAddress() {
        when(identity.login(any())).thenReturn(new AuthResponse("a", "Bearer", 1, user, tenant, "e", "f", "l", Set.of(), "ACTIVE"));
        when(refresh.issue(any(), any(), any(), any(), any())).thenReturn(new RefreshTokenService.IssuedToken("r", exp));
        when(http.getHeader("X-Forwarded-For")).thenReturn("41.0.0.9, 10.0.0.1");
        controller().login(new MobileLoginRequest("e@x.co.za", "pw", "acme", null), http);
        verify(refresh).issue(eq(user), eq(tenant), isNull(), eq("41.0.0.9"), any());
    }

    @Test
    void refresh_rotates_andReturnsTheNewRefreshToken() {
        when(refresh.refresh(eq("old"), eq("device-1"), any(), any())).thenReturn(new RefreshTokenService.RefreshResult(
                "new-access", 3600, user, tenant, "d@x.co.za", "Sipho", "D", Set.of("FUEL_DRIVER"), "new-refresh", exp));
        when(http.getRemoteAddr()).thenReturn("10.0.0.1");

        MobileSession s = controller().refresh(new MobileRefreshRequest("old", "device-1"), http).getBody().getData();

        assertThat(s.accessToken()).isEqualTo("new-access");
        assertThat(s.refreshToken()).isEqualTo("new-refresh");
        assertThat(s.refreshToken()).isNotEqualTo("old");
    }

    @Test
    void logout_revokesOnlyThePresentedToken() {
        controller().logout(new MobileRefreshRequest("mine", null));
        verify(refresh).revokeToken("mine");
        verifyNoMoreInteractions(refresh);
    }
}
