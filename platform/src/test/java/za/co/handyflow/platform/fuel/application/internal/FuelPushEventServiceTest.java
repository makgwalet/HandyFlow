package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.application.internal.FuelPushService.Attempt;
import za.co.handyflow.platform.fuel.application.internal.FuelPushService.Outcome;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPushEventServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock FuelPushService push;
    @Mock AuditTrailFacade audit;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID U1 = UUID.randomUUID();
    static final UUID U2 = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    FuelPushEventService service;

    @BeforeEach
    void setUp() {
        service = new FuelPushEventService(jdbc, push, audit, new ObjectMapper());
        when(push.isEnabled()).thenReturn(true);
        when(jdbc.queryForList(startsWith("SELECT enabled"), eq(Boolean.class), any(), any(), any())).thenReturn(List.of());
        when(jdbc.update(startsWith("INSERT INTO fuel_push_events"), any(Object[].class))).thenReturn(1);
    }

    private int publish(String key, UUID... users) {
        return service.publish(T, FuelPushEventService.DIP_VARIANCE, key, List.of(users), "Title", "Body", Map.of("screen", "Overview"));
    }

    @Test @DisplayName("an alert is queued once per person and audited")
    void queuesPerPerson() {
        assertEquals(2, publish("DIP:1", U1, U2));
        verify(jdbc, times(2)).update(startsWith("INSERT INTO fuel_push_events"), any(Object[].class));
        verify(audit).recordAs(eq(T), eq("SYSTEM"), any(), any(), eq("FUEL_PUSH_QUEUED"), any(), any(), any(), any());
    }

    @Test @DisplayName("a duplicate event key queues nothing and writes no audit event")
    void duplicateEvent() {
        when(jdbc.update(startsWith("INSERT INTO fuel_push_events"), any(Object[].class))).thenReturn(0);
        assertEquals(0, publish("DIP:1", U1));
        verify(audit, never()).recordAs(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("a person who switched the event type off is recorded as skipped, not sent")
    void preferenceOff() {
        when(jdbc.queryForList(startsWith("SELECT enabled"), eq(Boolean.class), any(), eq(U1), any())).thenReturn(List.of(false));
        assertEquals(1, publish("DIP:2", U1, U2));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(startsWith("INSERT INTO fuel_push_events"), args.capture());
        assertTrue(args.getAllValues().stream().anyMatch(a -> java.util.Arrays.asList(a).contains("SKIPPED")));
        assertTrue(args.getAllValues().stream().anyMatch(a -> java.util.Arrays.asList(a).contains("PENDING")));
    }

    @Test @DisplayName("nothing is queued when push is off, there is nobody to tell, or the event type is unknown")
    void nothingToQueue() {
        assertEquals(0, publish("K"));
        assertEquals(0, service.publish(T, "MADE_UP", "K", List.of(U1), "t", "b", Map.of()));
        when(push.isEnabled()).thenReturn(false);
        assertEquals(0, publish("K", U1));
        verify(jdbc, never()).update(startsWith("INSERT INTO fuel_push_events"), any(Object[].class));
    }

    @Test @DisplayName("a database failure while queueing never reaches the caller")
    void neverThrows() {
        when(jdbc.update(startsWith("INSERT INTO fuel_push_events"), any(Object[].class))).thenThrow(new RuntimeException("db down"));
        assertEquals(0, publish("K", U1));
    }

    private Map<String, Object> row(int attempts) {
        return Map.of("id", UUID.randomUUID(), "tenant_id", T.getValue(), "event_type", "DIP_VARIANCE", "user_id", U1, "title", "T", "body", "B",
                "data_json", "{\"screen\":\"Overview\"}", "attempts", attempts, "dedup_key", "DIP:1");
    }

    private void due(Map<String, Object> r) {
        when(jdbc.queryForList(startsWith("SELECT id, tenant_id"), any(Object[].class))).thenReturn(List.of(r));
    }

    @Test @DisplayName("a delivered alert is marked sent")
    void sent() {
        due(row(0));
        when(push.attempt(eq(T), eq(U1), any(), any(), any())).thenReturn(new Attempt(Outcome.SENT, null));
        assertEquals(1, service.dispatchDue(NOW));
        verify(jdbc).update(contains("status = 'SENT'"), any(Object[].class));
    }

    @Test @DisplayName("a person with no phone is recorded and not retried")
    void noDevice() {
        due(row(0));
        when(push.attempt(any(), any(), any(), any(), any())).thenReturn(new Attempt(Outcome.NO_DEVICE, null));
        service.dispatchDue(NOW);
        verify(jdbc).update(contains("status = 'NO_DEVICE'"), any(Object[].class));
    }

    @Test @DisplayName("a failure is retried later with a growing wait")
    void failureRetries() {
        due(row(0));
        when(push.attempt(any(), any(), any(), any(), any())).thenReturn(new Attempt(Outcome.FAILED, "timeout"));
        service.dispatchDue(NOW);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("status = 'FAILED'"), args.capture());
        assertTrue(java.util.Arrays.asList(args.getValue()).contains(java.sql.Timestamp.from(NOW.plus(FuelPushEventService.BACKOFF[0]))));
    }

    @Test @DisplayName("after the last attempt it gives up and writes an audit event")
    void givesUp() {
        due(row(FuelPushEventService.MAX_ATTEMPTS - 1));
        when(push.attempt(any(), any(), any(), any(), any())).thenReturn(new Attempt(Outcome.FAILED, "timeout"));
        service.dispatchDue(NOW);
        verify(jdbc).update(contains("status = 'GAVE_UP'"), any(Object[].class));
        verify(audit).recordAs(eq(T), eq("SYSTEM"), any(), any(), eq("FUEL_PUSH_GAVE_UP"), any(), any(), any(), any());
    }

    @Test @DisplayName("nothing is sent when push is switched off")
    void disabledDispatch() {
        when(push.isEnabled()).thenReturn(false);
        assertEquals(0, service.dispatchDue(NOW));
        verify(push, never()).attempt(any(), any(), any(), any(), any());
    }

    @Test @DisplayName("preferences are per person and per company, default on, and unknown types are refused")
    void preferences() {
        assertThrows(IllegalArgumentException.class, () -> service.setPreferences(T, U1, Map.of("MADE_UP", true)));
        assertThrows(IllegalStateException.class, () -> service.setPreferences(T, null, Map.of("DIP_VARIANCE", false)));
        service.setPreferences(T, U1, Map.of("DIP_VARIANCE", false));
        verify(jdbc).update(contains("fuel_push_preferences"), eq(T.getValue()), eq(U1), eq("DIP_VARIANCE"), eq(false));
        Map<String, Boolean> all = service.preferences(T, U1);
        assertEquals(4, all.size());
        assertTrue(all.values().stream().allMatch(v -> v));
    }
}
