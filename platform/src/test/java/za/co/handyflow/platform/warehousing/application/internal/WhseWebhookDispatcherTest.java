package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseWebhookDispatcherTest {

    private JdbcTemplate jdbc;
    private WhseWebhookDispatcher dispatcher;
    private final UUID hook = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(i -> { ((Consumer<Object>) i.getArgument(0)).accept(null); return null; }).when(tx).executeWithoutResult(any());
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
        dispatcher = new WhseWebhookDispatcher(jdbc, tx);
    }

    private WhseWebhookDispatcher.Due due(int attempt) {
        return new WhseWebhookDispatcher.Due(UUID.randomUUID(), UUID.randomUUID(), hook, UUID.randomUUID(), "outbound.shipped", "https://x.example.com/h", "s", "{}", attempt);
    }

    @Test @DisplayName("a 2xx answer marks the delivery delivered and clears the failure count")
    void delivered() {
        dispatcher.record(due(1), new WhseWebhookDispatcher.Outcome(true, 200, "Delivered (200)"));
        verify(jdbc).update(contains("status = 'DELIVERED'"), eq(200), eq("Delivered (200)"), any());
        verify(jdbc).update(contains("consecutive_failures = 0"), eq(hook));
    }

    @Test @DisplayName("a failure schedules a retry and counts against the webhook")
    void retry() {
        when(jdbc.queryForObject(contains("RETURNING consecutive_failures"), eq(Integer.class), any(Object[].class))).thenReturn(3);
        dispatcher.record(due(2), new WhseWebhookDispatcher.Outcome(false, 500, "The receiver had an error (500)"));
        verify(jdbc).update(contains("status = 'RETRY'"), any(), eq(500), anyString(), any());
        verify(jdbc, never()).update(contains("suspended = TRUE"), any(Object[].class));
    }

    @Test @DisplayName("the sixth failure is final: the delivery is dead")
    void dead() {
        when(jdbc.queryForObject(contains("RETURNING consecutive_failures"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        dispatcher.record(due(6), new WhseWebhookDispatcher.Outcome(false, null, "timeout"));
        verify(jdbc).update(contains("status = 'DEAD'"), isNull(), eq("timeout"), any());
    }

    @Test @DisplayName("twenty failures in a row suspend the webhook")
    void suspends() {
        when(jdbc.queryForObject(contains("RETURNING consecutive_failures"), eq(Integer.class), any(Object[].class))).thenReturn(20);
        dispatcher.record(due(1), new WhseWebhookDispatcher.Outcome(false, 502, "bad"));
        verify(jdbc).update(contains("suspended = TRUE"), eq(hook));
    }

    @Test @DisplayName("an address that is not allowed is not called and comes back as a failed outcome")
    void refusesPrivate() {
        WhseWebhookDispatcher.Outcome o = dispatcher.send("https://127.0.0.1/x", "s", "ping", UUID.randomUUID(), 1, "{}");
        assertFalse(o.ok());
        assertNull(o.code());
        assertTrue(o.detail().contains("private network"));
        o = dispatcher.send("http://example.com/x", "s", "ping", UUID.randomUUID(), 1, "{}");
        assertFalse(o.ok());
        assertTrue(o.detail().contains("https"));
    }
}
