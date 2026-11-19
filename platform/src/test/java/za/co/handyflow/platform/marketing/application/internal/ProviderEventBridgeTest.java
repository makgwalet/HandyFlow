package za.co.handyflow.platform.marketing.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** SES / SendGrid events must reach the delivery engine unchanged (this was an unmapped stub that threw). */
class ProviderEventBridgeTest {

    MarketingDeliveryEventService delivery = mock(MarketingDeliveryEventService.class);
    ProviderEventBridge bridge = new ProviderEventBridge(delivery);

    @Test void maps_every_field_to_the_delivery_engine() {
        UUID cc = UUID.randomUUID();
        Instant when = Instant.parse("2026-11-19T10:00:00Z");
        when(delivery.ingest(eq("ses"), any())).thenReturn(MarketingDeliveryEventService.IngestResult.APPLIED);

        var result = bridge.apply(new ProviderEvent("ses", "evt-9", "HARD_BOUNCE", cc, "a@x.test", when, "550 no such user"));

        assertEquals(MarketingDeliveryEventService.IngestResult.APPLIED, result);
        var cap = ArgumentCaptor.forClass(MarketingDeliveryEventService.ParsedEvent.class);
        verify(delivery).ingest(eq("ses"), cap.capture());
        var p = cap.getValue();
        assertEquals("evt-9", p.providerEventId());
        assertEquals("HARD_BOUNCE", p.type());
        assertEquals(cc, p.campaignContactId());
        assertEquals("a@x.test", p.email());
        assertEquals(when, p.occurredAt());
        assertEquals("550 no such user", p.detail());
    }

    @Test void unsupported_types_are_ignored_not_failed() {
        var r = bridge.apply(new ProviderEvent("sendgrid", "e1", "OPEN", UUID.randomUUID(), "a@x.test", Instant.now(), null));
        assertEquals(MarketingDeliveryEventService.IngestResult.IGNORED, r);
        verifyNoInteractions(delivery);
    }

    @Test void null_event_is_ignored() {
        assertEquals(MarketingDeliveryEventService.IngestResult.IGNORED, bridge.apply(null));
        verifyNoInteractions(delivery);
    }

    @Test void a_failure_in_the_engine_propagates_so_the_provider_retries() {
        when(delivery.ingest(any(), any())).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class,
                () -> bridge.apply(new ProviderEvent("ses", "e2", "COMPLAINT", UUID.randomUUID(), "a@x.test", Instant.now(), null)));
    }
}
