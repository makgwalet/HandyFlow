package za.co.handyflow.platform.warehousing.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseOutboxTest {

    @Test @DisplayName("an event is stored with its envelope and fanned out to the matching webhooks for the event type")
    void recordsAndFansOut() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhseOutbox outbox = new WhseOutbox(jdbc, new ObjectMapper());
        UUID tenant = UUID.randomUUID(), agg = UUID.randomUUID();

        UUID id = outbox.record(TenantId.of(tenant), "outbound.shipped", "OUTBOUND_ORDER", agg, Map.of("orderReference", "SO-1"));

        ArgumentCaptor<Object> json = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO whse_outbox_events"), eq(id), eq(tenant), eq("outbound.shipped"), eq("OUTBOUND_ORDER"), eq(agg), json.capture());
        String body = (String) json.getValue();
        assertTrue(body.contains("\"type\":\"outbound.shipped\""));
        assertTrue(body.contains("\"orderReference\":\"SO-1\""));
        assertTrue(body.contains(id.toString()));
        verify(jdbc).update(contains("INSERT INTO whse_webhook_deliveries"), eq(id), eq(tenant), eq("outbound.shipped"));
    }
}
