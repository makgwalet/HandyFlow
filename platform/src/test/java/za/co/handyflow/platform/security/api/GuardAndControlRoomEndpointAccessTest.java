package za.co.handyflow.platform.security.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import za.co.handyflow.platform.security.application.internal.CloseProtectionService;
import za.co.handyflow.platform.security.dto.GuardCpProfileResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security audit 1.2 / 1.4 / P1: the endpoints that had no authority check now have one, and the guard CP profile cannot be asked about another guard.
 * Reads the annotations by reflection, so it needs no Spring context. NOT RUN by the author (no Maven in the authoring sandbox).
 */
class GuardAndControlRoomEndpointAccessTest {

    @AfterEach void clean() { TenantContext.clear(); }

    private static String authority(Class<?> c, String method) {
        Method m = Arrays.stream(c.getDeclaredMethods()).filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        PreAuthorize p = m.getAnnotation(PreAuthorize.class);
        return p == null ? null : p.value();
    }

    @Test @DisplayName("raising an alarm or a duress from the web needs SECURITY_MANAGE")
    void controlRoomIngestAndDuress() {
        assertThat(authority(ControlRoomController.class, "ingest")).isEqualTo("hasAuthority('SECURITY_MANAGE')");
        assertThat(authority(ControlRoomController.class, "triggerDuress")).isEqualTo("hasAuthority('SECURITY_MANAGE')");
    }

    @Test @DisplayName("asking which guard is on a device needs SECURITY_READ")
    void resolveGuard() {
        assertThat(authority(DeviceSessionController.class, "resolveGuardId")).isEqualTo("hasAuthority('SECURITY_READ')");
    }

    @Test @DisplayName("the guard CP profile is on the guard path, needs SECURITY_GUARD and takes no guard id")
    void cpProfileShape() throws Exception {
        assertThat(GuardCpController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1/guard/cp");
        Method m = GuardCpController.class.getDeclaredMethod("getGuardCpProfile");
        assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('SECURITY_GUARD')");
        assertThat(m.getAnnotation(GetMapping.class).value()).containsExactly("/profile");
        assertThat(m.getParameterCount()).as("no guardId parameter: the guard comes from the token").isZero();
    }

    @Test @DisplayName("the profile is always looked up for the signed-in guard in their own tenant")
    void cpProfileUsesTheToken() {
        CloseProtectionService cp = mock(CloseProtectionService.class);
        when(cp.getGuardCpProfile(any(), any())).thenReturn(mock(GuardCpProfileResponse.class));
        UUID tenant = UUID.randomUUID(), guard = UUID.randomUUID();
        TenantContext.setTenantId(tenant.toString());
        TenantContext.setUserId(guard.toString());
        new GuardCpController(cp).getGuardCpProfile();
        verify(cp).getGuardCpProfile(TenantId.of(tenant.toString()), guard);
    }
}
