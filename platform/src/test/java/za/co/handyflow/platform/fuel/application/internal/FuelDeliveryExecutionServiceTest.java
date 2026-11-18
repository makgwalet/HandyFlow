package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.ExceptionView;
import za.co.handyflow.platform.fuel.dto.DeliveryExecutionDtos.Event;
import za.co.handyflow.platform.fuel.dto.DeliveryExecutionDtos.Execution;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Severity;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.EvidenceView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The office's timeline for one load: every record in time order, problems marked, nothing invented. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDeliveryExecutionServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelArrivalCheckService arrivalChecks;
    @Mock FuelEvidenceService evidence;
    @Mock FuelDeliveryExceptionService exceptions;
    @InjectMocks FuelDeliveryExecutionService service;

    static Instant at(String hhmm) { return Instant.parse("2026-11-18T" + hhmm + ":00Z"); }

    private FuelDelivery load() {
        FuelDelivery d = mock(FuelDelivery.class);
        when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getStatus()).thenReturn("DELIVERED");
        when(d.getCreatedAt()).thenReturn(at("05:00")); when(d.getScheduledAt()).thenReturn(at("08:00"));
        when(d.getLoadedAt()).thenReturn(at("07:00")); when(d.getLoadedLitres()).thenReturn(new BigDecimal("10000.00")); when(d.getSealNumbers()).thenReturn("A1, A2");
        when(d.getDeliveredAt()).thenReturn(at("11:00")); when(d.getLitresDelivered()).thenReturn(new BigDecimal("9980")); when(d.getReceiverName()).thenReturn("Sipho");
        when(deliveries.findActiveById(T, d.getId())).thenReturn(Optional.of(d));
        when(arrivalChecks.history(any(), any())).thenReturn(List.of());
        when(evidence.list(any(), any())).thenReturn(List.of());
        when(exceptions.list(any(), any())).thenReturn(List.of());
        return d;
    }

    private EvidenceView photo(String kind, Instant when, String status, String superseded) {
        return new EvidenceView(UUID.randomUUID(), null, kind, null, null, null, null, "at gate", when, null, status, superseded, null, null, false, true, "image/jpeg", 100);
    }

    @Test @DisplayName("events from every source come out oldest first, with problems marked")
    void orderedAndMarked() {
        FuelDelivery d = load();
        when(arrivalChecks.history(T, d.getId())).thenReturn(List.of(
                new ArrivalCheckView(d.getId(), false, List.of(new Finding("X", Severity.BLOCK, "Tanker cert expired")), at("09:00")),
                new ArrivalCheckView(d.getId(), true, List.of(), at("09:30"))));
        when(evidence.list(T, d.getId())).thenReturn(List.of(photo("SIGNATURE", at("10:50"), "ACTIVE", null)));
        when(exceptions.list(T, d.getId())).thenReturn(List.of(new ExceptionView(UUID.randomUUID(), d.getId(), "FAILED_ATTEMPT", "SITE_CLOSED", "gate locked", new BigDecimal("10000"), null, 1, null, at("09:10"))));
        Execution x = service.execution(T, d.getId());
        assertEquals(List.of("SCHEDULED", "LOADED", "ARRIVAL_CHECK", "FAILED_ATTEMPT", "ARRIVAL_CHECK", "EVIDENCE", "DELIVERED"), x.events().stream().map(Event::type).toList());
        assertEquals("PROBLEM", x.events().get(2).severity());
        assertTrue(x.events().get(2).text().contains("Tanker cert expired"));
        assertTrue(x.events().get(3).text().contains("Site closed"));
        assertTrue(x.events().get(1).text().contains("seals A1, A2"));
        assertTrue(x.events().get(6).text().contains("received by Sipho"));
    }

    @Test @DisplayName("a replaced piece of evidence is shown as replaced, not as good")
    void replacedEvidence() {
        FuelDelivery d = load();
        when(evidence.list(T, d.getId())).thenReturn(List.of(photo("PHOTO", at("10:00"), "SUPERSEDED", "wrong site")));
        Event e = service.execution(T, d.getId()).events().stream().filter(x -> x.type().equals("EVIDENCE")).findFirst().orElseThrow();
        assertEquals("REVIEW", e.severity());
        assertTrue(e.text().contains("replaced: wrong site"));
    }

    @Test @DisplayName("a load with nothing recorded beyond its creation has only that event")
    void sparse() {
        FuelDelivery d = mock(FuelDelivery.class);
        when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getStatus()).thenReturn("SCHEDULED"); when(d.getCreatedAt()).thenReturn(at("05:00"));
        when(deliveries.findActiveById(T, d.getId())).thenReturn(Optional.of(d));
        when(arrivalChecks.history(any(), any())).thenReturn(List.of()); when(evidence.list(any(), any())).thenReturn(List.of()); when(exceptions.list(any(), any())).thenReturn(List.of());
        assertEquals(1, service.execution(T, d.getId()).events().size());
    }

    @Test @DisplayName("an unknown load is refused")
    void unknown() {
        when(deliveries.findActiveById(any(), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.execution(T, UUID.randomUUID()));
    }

    @Test @DisplayName("reason codes read as words")
    void labels() {
        assertEquals("Site closed", FuelDeliveryExecutionService.label("SITE_CLOSED"));
        assertEquals("Unknown", FuelDeliveryExecutionService.label(null));
    }
}
