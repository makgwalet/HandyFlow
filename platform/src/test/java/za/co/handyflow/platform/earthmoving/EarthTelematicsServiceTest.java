package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.application.internal.TelematicsProvider.ProviderReading;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class EarthTelematicsServiceTest {
    EarthTelematicsDeviceRepository devices = mock(EarthTelematicsDeviceRepository.class);
    EarthTelematicsReadingRepository readings = mock(EarthTelematicsReadingRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    ObjectProvider<TelematicsProvider> providers = mock(ObjectProvider.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthTelematicsService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    EarthAsset machine;
    BigDecimal lat = new BigDecimal("-26.2041"), lon = new BigDecimal("28.0473");

    @BeforeEach
    void setUp() {
        service = new EarthTelematicsService(devices, readings, assets, providers, trail, access);
        machine = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", "Caterpillar", "D9T", 2020, "S1", "GP1",
                "OWN", null, null, null, new BigDecimal("18500"), new BigDecimal("2200"), null);
        when(assets.findActiveById(tenant, machine.getId())).thenReturn(Optional.of(machine));
        when(assets.findAllActive(eq(tenant), any())).thenReturn(new PageImpl<>(List.of(machine)));
        when(devices.save(any())).thenAnswer(i -> i.getArgument(0));
        when(readings.save(any())).thenAnswer(i -> i.getArgument(0));
        when(devices.findActiveDevice(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(devices.findActiveForAsset(any(), any(), anyString())).thenReturn(Optional.empty());
        when(providers.orderedStream()).thenReturn(Stream.empty());
    }

    private MapTelematicsDeviceRequest mapReq() { return new MapTelematicsDeviceRequest(machine.getId(), "acme", "DEV-1", "Dozer unit"); }

    private EarthTelematicsDevice mapped() {
        EarthTelematicsDevice d = EarthTelematicsDevice.map(tenant, machine.getId(), "ACME", "DEV-1", null, actor);
        when(devices.findActiveDevice(tenant, "ACME", "DEV-1")).thenReturn(Optional.of(d));
        return d;
    }

    @Test void mappingNeedsAdmin() {
        granted.add("EARTHMOVING_WRITE");
        assertThrows(HandyFlowException.class, () -> service.map(tenant, mapReq(), actor));
        verify(devices, never()).save(any());
    }

    @Test void mappingStoresTheDeviceAndIsAudited() {
        granted.add("EARTHMOVING_ADMIN");
        TelematicsDeviceResponse out = service.map(tenant, mapReq(), actor);
        assertEquals("ACME", out.providerCode());
        assertTrue(out.active());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_TELEMATICS_DEVICE_MAPPED"), anyString(), any(), any(), any());
    }

    @Test void aDeviceCannotBeOnTwoMachinesNorAMachineOnTwoOfTheSameProvider() {
        granted.add("EARTHMOVING_ADMIN");
        EarthTelematicsDevice other = EarthTelematicsDevice.map(tenant, UUID.randomUUID(), "ACME", "DEV-1", null, actor);
        when(devices.findActiveDevice(tenant, "ACME", "DEV-1")).thenReturn(Optional.of(other));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.map(tenant, mapReq(), actor)).getMessage().contains("another machine"));
        when(devices.findActiveDevice(tenant, "ACME", "DEV-1")).thenReturn(Optional.empty());
        when(devices.findActiveForAsset(tenant, machine.getId(), "ACME")).thenReturn(Optional.of(other));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.map(tenant, mapReq(), actor)).getMessage().contains("already has"));
        verify(devices, never()).save(any());
    }

    @Test void unmappingNeedsAdminAndAReason() {
        EarthTelematicsDevice d = EarthTelematicsDevice.map(tenant, machine.getId(), "ACME", "DEV-1", null, actor);
        when(devices.findByTenantAndId(tenant, d.getId())).thenReturn(Optional.of(d));
        assertThrows(HandyFlowException.class, () -> service.unmap(tenant, d.getId(), new UnmapTelematicsDeviceRequest("Unit removed"), actor));
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.unmap(tenant, d.getId(), new UnmapTelematicsDeviceRequest(""), actor));
        assertFalse(service.unmap(tenant, d.getId(), new UnmapTelematicsDeviceRequest("Unit removed"), actor).active());
    }

    @Test void ingestStoresGoodReadingsSkipsDuplicatesAndReportsBadOnes() {
        EarthTelematicsDevice d = mapped();
        Instant now = Instant.now();
        Instant dup = now.minusSeconds(120);
        when(readings.countDuplicate(d.getId(), dup)).thenReturn(1L);
        List<ProviderReading> batch = List.of(
                new ProviderReading(now.minusSeconds(60), lat, lon, new BigDecimal("1200"), "HOURS"),
                new ProviderReading(dup, lat, lon, null, null),
                new ProviderReading(now.plus(Duration.ofHours(2)), lat, lon, null, null),
                new ProviderReading(now.minusSeconds(30), null, null, null, null));
        TelematicsIngestResult out = service.ingest(tenant, "acme", "DEV-1", batch);
        assertEquals(1, out.accepted());
        assertEquals(1, out.duplicates());
        assertEquals(2, out.rejected().size());
        verify(readings, times(1)).save(any());
    }

    @Test void ingestForAnUnmappedDeviceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.ingest(tenant, "ACME", "NOPE", List.of()));
    }

    @Test void aManualReadingNeedsWriteAndNeverChangesTheMachinesMeter() {
        BigDecimal before = machine.getCurrentHours();
        assertThrows(HandyFlowException.class, () -> service.recordManual(tenant, machine.getId(), new ManualTelematicsReadingRequest(null, null, null, new BigDecimal("5000"), "HOURS", null), actor));
        granted.add("EARTHMOVING_WRITE");
        TelematicsReadingResponse out = service.recordManual(tenant, machine.getId(), new ManualTelematicsReadingRequest(null, null, null, new BigDecimal("5000"), "HOURS", "dash"), actor);
        assertEquals("MANUAL", out.source());
        assertEquals(before, machine.getCurrentHours());
        verify(assets, never()).save(any());
    }

    @Test void statusKeepsProviderAndManualApartAndNeverSaysLive() {
        granted.add("EARTHMOVING_READ");
        EarthTelematicsDevice d = EarthTelematicsDevice.map(tenant, machine.getId(), "ACME", "DEV-1", null, actor);
        Instant now = Instant.now();
        EarthTelematicsReading prov = EarthTelematicsReading.fromProvider(tenant, d, now.minus(Duration.ofMinutes(5)), now, lat, lon, new BigDecimal("12.00"), "HOURS");
        EarthTelematicsReading man = EarthTelematicsReading.manual(tenant, machine.getId(), now.minus(Duration.ofDays(2)), now, lat, lon, null, null, null, actor);
        when(readings.findLatestPositionBySource(tenant, "PROVIDER")).thenReturn(List.of(prov));
        when(readings.findLatestMeterBySource(tenant, "PROVIDER")).thenReturn(List.of(prov));
        when(readings.findLatestPositionBySource(tenant, "MANUAL")).thenReturn(List.of(man));
        when(readings.findLatestMeterBySource(tenant, "MANUAL")).thenReturn(List.of());
        when(devices.findActive(tenant)).thenReturn(List.of(d));
        TrackingStatusResponse s = service.status(tenant).get(0);
        assertEquals("FRESH", s.provider().freshness());
        assertEquals("OFFLINE", s.manual().freshness());
        assertEquals(1, s.activeDevices());
        assertEquals(0, new BigDecimal("12.00").compareTo(s.providerMeterDifference()));   // machine meter is 0
        assertNull(s.manual().meterValue());
    }

    @Test void aMachineNeverSeenSaysNever() {
        granted.add("EARTHMOVING_READ");
        when(readings.findLatestPositionBySource(any(), anyString())).thenReturn(List.of());
        when(readings.findLatestMeterBySource(any(), anyString())).thenReturn(List.of());
        when(devices.findActive(tenant)).thenReturn(List.of());
        TrackingStatusResponse s = service.status(tenant).get(0);
        assertEquals("NEVER", s.provider().freshness());
        assertNull(s.providerMeterDifference());
        assertEquals(0, s.activeDevices());
    }

    @Test void pollingWithNoAdapterInstalledDoesNothing() {
        assertEquals(0, service.pollInstalled(tenant));
        verify(devices, never()).findActive(any());
    }

    @Test void viewingNeedsRead() {
        assertThrows(HandyFlowException.class, () -> service.status(tenant));
        assertThrows(HandyFlowException.class, () -> service.devices(tenant, false));
    }
}
