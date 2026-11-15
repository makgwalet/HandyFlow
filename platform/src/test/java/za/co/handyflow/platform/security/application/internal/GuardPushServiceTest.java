package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Alerts go to the guard's own live tokens, only when switched on, and can never break the caller. */
class GuardPushServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final String TOKEN_A = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";
    private static final String TOKEN_B = "ExponentPushToken[bbbbbbbbbbbbbbbbbbbbbb]";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExpoPushClient client = mock(ExpoPushClient.class);
    private final UUID guard = UUID.randomUUID();

    private GuardPushService service(boolean enabled) {
        return new GuardPushService(jdbc, client, enabled, Runnable::run);
    }

    private void tokens(String... t) {
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of(t));
    }

    @Test
    void nothingIsReadOrSentWhenPushIsSwitchedOff() {
        service(false).notifyGuard(TENANT, guard, "Hi", "There", Map.of());
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), any(), any());
        verify(client, never()).send(any());
    }

    @Test
    void everyLiveTokenOfTheGuardGetsTheMessage() {
        tokens(TOKEN_A, TOKEN_B);
        when(client.send(any())).thenReturn(List.of(new ExpoPushClient.Result(true, false, null), new ExpoPushClient.Result(true, false, null)));
        service(true).notifyGuard(TENANT, guard, "New shift", "Tomorrow 06:00", Map.of("screen", "Home"));
        verify(client).send(List.of(
                new ExpoPushClient.Message(TOKEN_A, "New shift", "Tomorrow 06:00", Map.of("screen", "Home")),
                new ExpoPushClient.Message(TOKEN_B, "New shift", "Tomorrow 06:00", Map.of("screen", "Home"))));
    }

    @Test
    void aGuardWithNoTokenIsSkippedQuietly() {
        tokens();
        service(true).notifyGuard(TENANT, guard, "x", "y", Map.of());
        verify(client, never()).send(any());
    }

    @Test
    void aTokenExpoSaysIsDeadIsRevoked() {
        tokens(TOKEN_A, TOKEN_B);
        when(client.send(any())).thenReturn(List.of(new ExpoPushClient.Result(true, false, null), new ExpoPushClient.Result(false, true, "DeviceNotRegistered")));
        service(true).notifyGuard(TENANT, guard, "x", "y", Map.of());
        verify(jdbc, times(1)).update(contains("SET revoked_at"), any(), eq(TOKEN_B));
    }

    @Test
    void aFailingPushServiceNeverBreaksTheCaller() {
        tokens(TOKEN_A);
        when(client.send(any())).thenThrow(new RuntimeException("expo down"));
        assertThatCode(() -> service(true).notifyGuard(TENANT, guard, "x", "y", Map.of())).doesNotThrowAnyException();
    }

    @Test
    void aTokenThatIsNotAnExpoTokenIsRefused() {
        assertThatThrownBy(() -> service(true).register(TENANT, guard, "dev-1", "not-a-token"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not a valid push token");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void registeringRevokesThePhonesOldTokenThenSavesTheNewOne() {
        service(true).register(TENANT, guard, "dev-1", TOKEN_A);
        verify(jdbc).update(contains("SET revoked_at = NOW()"), any(), eq("dev-1"), eq(TOKEN_A));
        verify(jdbc).update(contains("INSERT INTO security_guard_push_tokens"), any(), eq(guard), eq("dev-1"), eq(TOKEN_A));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everySupervisorOnDutyAtThePressersSiteIsAlerted() {
        UUID sup1 = UUID.randomUUID();
        UUID sup2 = UUID.randomUUID();
        when(jdbc.query(contains("site_supervisor"), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any()))
                .thenReturn(List.of(sup1, sup2));
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of(TOKEN_A));
        when(client.send(any())).thenReturn(List.of(new ExpoPushClient.Result(true, false, null)));

        service(true).notifySiteSupervisorsOf(TENANT, guard, "PANIC ALERT", "x", Map.of("screen", "Supervisor"));

        verify(client, times(2)).send(any());
    }

    @Test
    void noSupervisorsOnDutyMeansNobodyIsAlertedAndNothingFails() {
        when(jdbc.query(contains("site_supervisor"), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());
        service(true).notifySiteSupervisorsOf(TENANT, guard, "PANIC ALERT", "x", Map.of());
        verify(client, never()).send(any());
    }

    @Test
    void supervisorAlertsAreOffWhenPushIsOff() {
        service(false).notifySiteSupervisorsOf(TENANT, guard, "PANIC ALERT", "x", Map.of());
        verify(jdbc, never()).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyoneOnDutyAtTheSiteIsAlertedWhenNotLimitedToSupervisors() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID site = UUID.randomUUID();
        when(jdbc.query(contains("security_device_sessions"), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(a, b));
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of(TOKEN_A));
        when(client.send(any())).thenReturn(List.of(new ExpoPushClient.Result(true, false, null)));

        service(true).notifyOnDutyAt(TENANT, site, false, null, "Post orders updated", "x", Map.of("screen", "MyPost"));

        verify(client, times(2)).send(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDutyAlertsDoNothingWhenPushIsOffOrNoSiteGiven() {
        service(false).notifyOnDutyAt(TENANT, UUID.randomUUID(), true, null, "t", "b", Map.of());
        service(true).notifyOnDutyAt(TENANT, null, true, null, "t", "b", Map.of());
        verify(jdbc, never()).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any(), any());
    }
}
