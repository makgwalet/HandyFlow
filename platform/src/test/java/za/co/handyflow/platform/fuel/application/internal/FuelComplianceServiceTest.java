package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ComplianceRequest;
import za.co.handyflow.platform.fuel.dto.RenewRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelComplianceServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

    @Mock FuelComplianceItemRepository items;
    @Mock FuelComplianceRenewalRepository renewals;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock HrFacade hr;
    @Mock FuelLegalEntityRepository legalEntities;
    @Mock FuelComplianceEvidenceRepository evidence;
    @InjectMocks FuelComplianceService service;

    private FuelComplianceItem item(String subject, UUID id, LocalDate expires) {
        return FuelComplianceItem.create(T, "CERTIFICATE", "Roadworthy", null, subject, id, "GP 12 AB", null, expires, null);
    }

    @Test @DisplayName("status is VALID, EXPIRING within 30 days, EXPIRED after the date")
    void status() {
        assertEquals("VALID", item("COMPANY", null, TODAY.plusDays(31)).statusOn(TODAY));
        assertEquals("EXPIRING", item("COMPANY", null, TODAY.plusDays(30)).statusOn(TODAY));
        assertEquals("EXPIRING", item("COMPANY", null, TODAY).statusOn(TODAY));
        assertEquals("EXPIRED", item("COMPANY", null, TODAY.minusDays(1)).statusOn(TODAY));
    }

    @Test @DisplayName("a tanker item needs a tanker chosen")
    void subjectRequired() {
        assertThrows(IllegalArgumentException.class, () -> item("TANKER", null, TODAY.plusDays(5)));
    }

    @Test @DisplayName("renewing cannot shorten validity")
    void renewMustExtend() {
        FuelComplianceItem i = item("COMPANY", null, TODAY.plusDays(10));
        assertThrows(IllegalArgumentException.class, () -> i.renew(TODAY.plusDays(10), null));
        assertEquals(TODAY.plusDays(10), i.renew(TODAY.plusDays(375), null));
        assertEquals(TODAY.plusDays(375), i.getExpiresOn());
    }

    @Test @DisplayName("renew keeps a history row")
    void renewHistory() {
        UUID id = UUID.randomUUID();
        FuelComplianceItem i = item("COMPANY", null, TODAY.plusDays(10));
        when(items.findActiveById(T, id)).thenReturn(Optional.of(i));
        when(items.save(any())).thenAnswer(a -> a.getArgument(0));
        service.renew(T, id, new RenewRequest(TODAY.plusDays(400), null, "annual"), UUID.randomUUID());
        verify(renewals).save(any());
    }

    @Test @DisplayName("allocation is refused and names every expired item")
    void blocksAllocation() {
        UUID tanker = UUID.randomUUID(), driver = UUID.randomUUID();
        when(items.findExpiredFor(T, "TANKER", tanker, TODAY)).thenReturn(List.of(item("TANKER", tanker, TODAY.minusDays(3))));
        when(items.findExpiredFor(T, "DRIVER", driver, TODAY)).thenReturn(List.of(item("DRIVER", driver, TODAY.minusDays(1))));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.requireCompliantForAllocation(T, tanker, "GP 12 AB", driver, "Sipho"));
        assertTrue(e.getMessage().contains("GP 12 AB"));
        assertTrue(e.getMessage().contains("Sipho"));
    }

    @Test @DisplayName("nothing expired lets allocation through")
    void allows() {
        assertDoesNotThrow(() -> service.requireCompliantForAllocation(T, UUID.randomUUID(), "GP 1", UUID.randomUUID(), "Sipho"));
    }
}
