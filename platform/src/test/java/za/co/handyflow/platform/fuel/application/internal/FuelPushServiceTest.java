package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPushServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final String TOKEN = "ExponentPushToken[abcdefghijklmnop]";

    @Mock JdbcTemplate jdbc;
    @Mock FuelExpoPushClient client;
    FuelPushService on, off;
    final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        on = new FuelPushService(jdbc, client, true, Runnable::run);
        off = new FuelPushService(jdbc, client, false, Runnable::run);
    }

    @Test @DisplayName("only a real Expo token is accepted")
    void tokenShape() {
        assertThrows(HandyFlowException.class, () -> on.register(T, user, "dev1", "not-a-token"));
        assertThrows(HandyFlowException.class, () -> on.register(T, user, "dev1", null));
        assertDoesNotThrow(() -> on.register(T, user, "dev1", TOKEN));
    }

    @Test @DisplayName("a device id is required and kept short")
    void deviceRequired() {
        assertThrows(HandyFlowException.class, () -> on.register(T, user, " ", TOKEN));
        assertThrows(HandyFlowException.class, () -> on.register(T, user, "x".repeat(101), TOKEN));
    }

    @Test @DisplayName("registering moves the phone's token to the new user and revokes any other token for that phone")
    void tokenMoves() {
        on.register(T, user, "dev1", TOKEN);
        verify(jdbc).update(contains("SET revoked_at = NOW()"), eq(T.getValue()), eq("dev1"), eq(TOKEN));
        verify(jdbc).update(contains("ON CONFLICT (tenant_id, expo_token) DO UPDATE SET user_id = EXCLUDED.user_id"), eq(T.getValue()), eq(user), eq("dev1"), eq(TOKEN));
    }

    @Test @DisplayName("sign-out stops alerts for that user on that phone only")
    void signOut() {
        on.unregister(T, user, "dev1");
        verify(jdbc).update(contains("user_id = ? AND device_id = ?"), eq(T.getValue()), eq(user), eq("dev1"));
    }

    @Test @DisplayName("switched off, nothing is looked up or sent")
    void disabled() {
        off.notifyUser(T, user, "t", "b", Map.of());
        verifyNoInteractions(jdbc, client);
    }

    @Test @DisplayName("a user with no phone registered sends nothing")
    void noTokens() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of());
        on.notifyUser(T, user, "t", "b", Map.of());
        verify(client, never()).send(any());
    }

    @Test @DisplayName("every phone of the user is sent the message, and a dead token is revoked")
    void sendsAndRevokesDead() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of("ExponentPushToken[aaaaaaaaaaaa]", "ExponentPushToken[bbbbbbbbbbbb]"));
        when(client.send(any())).thenReturn(List.of(new FuelExpoPushClient.Result(true, false, null), new FuelExpoPushClient.Result(false, true, "DeviceNotRegistered")));
        on.notifyUser(T, user, "New load", "Diesel today", Map.of("screen", "LoadDetail"));
        verify(client).send(argThat(m -> m.size() == 2 && m.get(0).title().equals("New load") && m.get(1).data().get("screen").equals("LoadDetail")));
        verify(jdbc).update(contains("SET revoked_at"), eq(T.getValue()), eq("ExponentPushToken[bbbbbbbbbbbb]"));
        verify(jdbc, never()).update(contains("SET revoked_at"), eq(T.getValue()), eq("ExponentPushToken[aaaaaaaaaaaa]"));
    }

    @Test @DisplayName("the same person named twice is alerted once")
    void dedupes() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of("ExponentPushToken[aaaaaaaaaaaa]"));
        when(client.send(any())).thenReturn(List.of(new FuelExpoPushClient.Result(true, false, null)));
        on.notifyUsers(T, List.of(user, user), "t", "b", Map.of());
        verify(client, times(1)).send(any());
    }

    @Test @DisplayName("a failing push service never throws into the action that caused the alert")
    void neverThrows() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenReturn(List.of("ExponentPushToken[aaaaaaaaaaaa]"));
        when(client.send(any())).thenThrow(new RuntimeException("expo down"));
        assertDoesNotThrow(() -> on.notifyUser(T, user, "t", "b", Map.of()));
        when(jdbc.queryForList(anyString(), eq(String.class), any(), any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> on.notifyUser(T, user, "t", "b", Map.of()));
    }
}
