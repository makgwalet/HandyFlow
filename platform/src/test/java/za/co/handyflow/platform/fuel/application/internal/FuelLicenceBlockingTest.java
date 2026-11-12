package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ComplianceLinkageDtos.Coverage;
import za.co.handyflow.platform.fuel.dto.ComplianceRequest;
import za.co.handyflow.platform.fuel.dto.ComplianceResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** F01 leftovers: licence-to-depot link, site coverage and the opt-in block on loading. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelLicenceBlockingTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

    @Mock FuelComplianceItemRepository items;
    @Mock FuelComplianceRenewalRepository renewals;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock HrFacade hr;
    @Mock FuelLegalEntityRepository legalEntities;
    @Mock FuelComplianceEvidenceRepository evidence;
    @Mock FuelSettingsService settings;
    @InjectMocks FuelComplianceService service;

    FuelLegalEntity acme;
    FuelDepot depot, other;
    FuelTank tank;
    FuelSettings cfg;

    @BeforeEach
    void setUp() {
        acme = FuelLegalEntity.create(T, "Acme Fuels (Pty) Ltd", null, null, null, null, USER);
        depot = FuelDepot.create(T, "Germiston", "GER", null);
        depot.assignLegalEntity(acme.getId());
        other = FuelDepot.create(T, "Durban", "DBN", null);
        tank = FuelTank.create(T, "T1", "DIESEL_50", new BigDecimal("10000"), null, null);
        tank.place(depot.getId(), BigDecimal.ZERO);
        cfg = FuelSettings.forTenant(T);
        when(settings.current(T)).thenReturn(cfg);
        when(legalEntities.findAllActive(T)).thenReturn(List.of(acme));
        when(legalEntities.findActiveById(T, acme.getId())).thenReturn(Optional.of(acme));
        when(depots.findAllActive(T)).thenReturn(List.of(depot, other));
        when(depots.findActiveById(T, depot.getId())).thenReturn(Optional.of(depot));
        when(depots.findActiveById(T, other.getId())).thenReturn(Optional.of(other));
        when(tanks.findActiveById(T, tank.getId())).thenReturn(Optional.of(tank));
        when(items.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private FuelComplianceItem licence(String activity, int daysFromToday, UUID entity, UUID depotId) {
        FuelComplianceItem i = FuelComplianceItem.create(T, "LICENCE", activity + " licence", null, "COMPANY", null, "Company", null, TODAY.plusDays(daysFromToday), null);
        i.applyScope(entity, activity, null);
        i.linkDepot(depotId);
        return i;
    }

    @Test @DisplayName("with nothing chosen in Settings (the default) an expired licence does not block loading")
    void offByDefault() {
        when(items.findAllActive(T)).thenReturn(List.of(licence("WHOLESALE", -5, acme.getId(), null)));
        assertDoesNotThrow(() -> service.requireLoadingSiteLicensed(T, tank.getId()));
    }

    @Test @DisplayName("an expired licence for a chosen activity on the loading depot's legal entity blocks, naming the entity, depot and date")
    void blocksOnEntity() {
        cfg.updateLicenceBlocking(List.of("wholesale"));
        when(items.findAllActive(T)).thenReturn(List.of(licence("WHOLESALE", -5, acme.getId(), null)));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.requireLoadingSiteLicensed(T, tank.getId()));
        assertTrue(ex.getMessage().contains("Acme Fuels (Pty) Ltd")); assertTrue(ex.getMessage().contains("Germiston")); assertTrue(ex.getMessage().contains(TODAY.minusDays(5).toString()));
    }

    @Test @DisplayName("a licence linked to the loading depot itself blocks even with no legal entity")
    void blocksOnDepotLink() {
        cfg.updateLicenceBlocking(List.of("STORAGE"));
        when(items.findAllActive(T)).thenReturn(List.of(licence("STORAGE", -1, null, depot.getId())));
        assertThrows(IllegalStateException.class, () -> service.requireLoadingSiteLicensed(T, tank.getId()));
    }

    @Test @DisplayName("not blocked: an activity that was not chosen, a licence still valid, another entity, another depot, or a non-licence item")
    void doesNotBlockOthers() {
        cfg.updateLicenceBlocking(List.of("WHOLESALE"));
        FuelComplianceItem cert = FuelComplianceItem.create(T, "CERTIFICATE", "Cert", null, "COMPANY", null, "Company", null, TODAY.minusDays(3), null);
        cert.applyScope(acme.getId(), "WHOLESALE", null);
        when(items.findAllActive(T)).thenReturn(List.of(
                licence("RETAIL", -5, acme.getId(), null),                 // activity not chosen
                licence("WHOLESALE", 40, acme.getId(), null),              // still valid
                licence("WHOLESALE", -5, UUID.randomUUID(), null),         // someone else's entity
                licence("WHOLESALE", -5, null, other.getId()),             // a different depot
                cert));                                                    // not a licence
        assertDoesNotThrow(() -> service.requireLoadingSiteLicensed(T, tank.getId()));
    }

    @Test @DisplayName("a tank with no depot, or no tank, is not blocked")
    void noDepotNoBlock() {
        cfg.updateLicenceBlocking(List.of("WHOLESALE"));
        when(items.findAllActive(T)).thenReturn(List.of(licence("WHOLESALE", -5, acme.getId(), null)));
        FuelTank loose = FuelTank.create(T, "T2", "DIESEL_50", BigDecimal.TEN, null, null);
        when(tanks.findActiveById(T, loose.getId())).thenReturn(Optional.of(loose));
        assertDoesNotThrow(() -> service.requireLoadingSiteLicensed(T, loose.getId()));
        assertDoesNotThrow(() -> service.requireLoadingSiteLicensed(T, null));
    }

    @Test @DisplayName("settings: unknown activities are refused, null leaves the choice alone, empty switches it off, values are stored sorted and upper-case")
    void settingsRules() {
        assertThrows(IllegalArgumentException.class, () -> cfg.updateLicenceBlocking(List.of("MINING")));
        cfg.updateLicenceBlocking(List.of("storage", "wholesale", "STORAGE"));
        assertEquals(java.util.Set.of("STORAGE", "WHOLESALE"), cfg.blockingActivities());
        cfg.updateLicenceBlocking(null);
        assertEquals(2, cfg.blockingActivities().size());
        cfg.updateLicenceBlocking(List.of());
        assertTrue(cfg.blockingActivities().isEmpty());
    }

    @Test @DisplayName("creating a licence tied to a depot returns the depot's name; a depot from another tenant is a 404 and nothing is saved")
    void depotLink() {
        ComplianceResponse r = service.create(T, new ComplianceRequest("LICENCE", "Storage", null, "COMPANY", null, null, null, TODAY.plusDays(90), null, null, "STORAGE", null, depot.getId()));
        assertEquals(depot.getId(), r.depotId()); assertEquals("Germiston", r.depotName());
        UUID stranger = UUID.randomUUID();
        when(depots.findActiveById(T, stranger)).thenReturn(Optional.empty());
        clearInvocations(items);
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, new ComplianceRequest("LICENCE", "Storage", null, "COMPANY", null, null, null, TODAY.plusDays(90), null, null, "STORAGE", null, stranger)));
        verify(items, never()).save(any());
    }

    @Test @DisplayName("site coverage counts licences per depot by status and leaves out depots with none")
    void siteCoverage() {
        when(items.findAllActive(T)).thenReturn(List.of(licence("STORAGE", -2, null, depot.getId()), licence("STORAGE", 90, null, depot.getId()), licence("WHOLESALE", 10, acme.getId(), null)));
        Coverage c = service.coverage(T);
        assertEquals(1, c.sites().size());
        assertEquals("Germiston", c.sites().get(0).name()); assertEquals(1, c.sites().get(0).expired()); assertEquals(1, c.sites().get(0).valid());
        assertEquals("Acme Fuels (Pty) Ltd", c.sites().get(0).legalEntityName());
    }
}
