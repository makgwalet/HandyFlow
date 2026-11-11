package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDistributionControlsServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), DRIVER_EMP = UUID.randomUUID();

    @Mock FuelDeliveryVarianceRepository variances;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelSettingsService settings;
    @Mock FuelDeliveryEvidenceRepository evidence;
    @Mock HrFacade hr;

    FuelVarianceService varianceService;
    FuelEvidenceService evidenceService;
    FuelSettings cfg;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        varianceService = new FuelVarianceService(variances, deliveries, settings);
        evidenceService = new FuelEvidenceService(deliveries, evidence, hr);
        cfg = FuelSettings.forTenant(T);
        when(settings.current(any(TenantId.class))).thenReturn(cfg);
        when(variances.save(any())).thenAnswer(i -> i.getArgument(0));
        when(evidence.save(any())).thenAnswer(i -> i.getArgument(0));
        when(variances.findAllForTenant(any(TenantId.class))).thenReturn(List.of());
    }

    private FuelDelivery delivery(String status, String loaded, String delivered) {
        FuelDelivery d = mock(FuelDelivery.class);
        UUID id = UUID.randomUUID();
        when(d.getId()).thenReturn(id);
        when(d.getStatus()).thenReturn(status);
        when(d.getLoadedLitres()).thenReturn(bd(loaded));
        when(d.getLitresDelivered()).thenReturn(bd(delivered));
        when(d.getDriverEmployeeId()).thenReturn(DRIVER_EMP);
        when(deliveries.findActiveById(any(TenantId.class), eq(id))).thenReturn(Optional.of(d));
        return d;
    }

    // ── variance ─────────────────────────────────────────────────────────────
    @Test @DisplayName("with no tolerance set the list says so and nothing is scanned or raised")
    void noToleranceNoScan() {
        VarianceList out = varianceService.list(T);
        assertFalse(out.toleranceSet());
        verify(deliveries, never()).findUncheckedForVariance(any(TenantId.class), any());
        verify(variances, never()).save(any());
    }

    @Test @DisplayName("only deliveries outside the tolerance raise a variance")
    void detectsOutsideOnly() {
        cfg.updateVarianceTolerance(bd("0.5"));
        FuelDelivery within = delivery("DELIVERED", "10000", "9960");
        FuelDelivery outside = delivery("DELIVERED", "10000", "9800");
        when(deliveries.findUncheckedForVariance(any(TenantId.class), any(Instant.class))).thenReturn(List.of(within, outside));
        assertEquals(1, varianceService.detect(T, bd("0.5")));
        verify(variances, times(1)).save(any());
    }

    @Test @DisplayName("a variance of another company cannot be reviewed")
    void otherTenantVariance() {
        when(variances.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> varianceService.close(T, UUID.randomUUID(), "EXPLAINED", "Meter drift confirmed", ALICE));
    }

    @Test @DisplayName("closing records the decision and reviewer and does not touch deliveries")
    void closeRecords() {
        FuelDeliveryVariance v = FuelDeliveryVariance.ifOutside(T, UUID.randomUUID(), bd("1000"), bd("900"), bd("1"));
        when(variances.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(v));
        VarianceView out = varianceService.close(T, v.getId(), "WRITTEN_OFF", "Spillage at the depot", BOB);
        assertEquals("CLOSED", out.status());
        assertEquals(BOB, out.reviewedBy());
        verify(deliveries, never()).save(any());
    }

    // ── evidence ─────────────────────────────────────────────────────────────
    private static EvidenceRequest photoReq() { return new EvidenceRequest("PHOTO", "files/pod.jpg", null, null, null, null); }

    @Test @DisplayName("office staff can add evidence to a delivered load but not to a scheduled or cancelled one")
    void addStatusRules() {
        FuelDelivery ok = delivery("DELIVERED", "1000", "1000");
        assertEquals("PHOTO", evidenceService.addAsOffice(T, ok.getId(), photoReq(), ALICE).kind());
        FuelDelivery scheduled = delivery("SCHEDULED", "1000", "1000");
        assertThrows(IllegalStateException.class, () -> evidenceService.addAsOffice(T, scheduled.getId(), photoReq(), ALICE));
        FuelDelivery cancelled = delivery("CANCELLED", "1000", "1000");
        assertThrows(IllegalStateException.class, () -> evidenceService.addAsOffice(T, cancelled.getId(), photoReq(), ALICE));
    }

    @Test @DisplayName("a driver can add evidence to their own load only; another driver's load looks missing")
    void driverOwnership() {
        EmployeeSummary me = new EmployeeSummary(DRIVER_EMP, "E1", "Dan", "Driver", "Dan Driver", null, null, "Driver", null, "ACTIVE");
        when(hr.findEmployeeByUserId(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(me));
        FuelDelivery mine = delivery("IN_TRANSIT", "1000", "1000");
        assertNotNull(evidenceService.addAsDriver(T, ALICE, mine.getId(), photoReq()));
        FuelDelivery theirs = delivery("IN_TRANSIT", "1000", "1000");
        when(theirs.getDriverEmployeeId()).thenReturn(UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class, () -> evidenceService.addAsDriver(T, ALICE, theirs.getId(), photoReq()));
        assertThrows(ResourceNotFoundException.class, () -> evidenceService.listAsDriver(T, ALICE, theirs.getId()));
    }

    @Test @DisplayName("a login not linked to an employee cannot add evidence")
    void driverNotLinked() {
        when(hr.findEmployeeByUserId(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> evidenceService.addAsDriver(T, ALICE, UUID.randomUUID(), photoReq()));
    }

    @Test @DisplayName("evidence for a delivery of another company is not found")
    void otherTenantDelivery() {
        when(deliveries.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> evidenceService.list(T, UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> evidenceService.addAsOffice(T, UUID.randomUUID(), photoReq(), ALICE));
    }

    @Test @DisplayName("verification by the capturer is refused; by another person locks it")
    void verifyFourEyes() {
        FuelDeliveryEvidence e = FuelDeliveryEvidence.create(T, UUID.randomUUID(), "PHOTO", "f.jpg", null, null, null, null, ALICE);
        when(evidence.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(e));
        assertThrows(IllegalStateException.class, () -> evidenceService.verify(T, e.getId(), ALICE));
        assertTrue(evidenceService.verify(T, e.getId(), BOB).locked());
    }

    @Test @DisplayName("replacing evidence keeps the old record and adds the new one")
    void supersedeWithReplacement() {
        FuelDelivery d = delivery("DELIVERED", "1000", "1000");
        FuelDeliveryEvidence e = FuelDeliveryEvidence.create(T, d.getId(), "PHOTO", "wrong.jpg", null, null, null, null, ALICE);
        when(evidence.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(e));
        List<EvidenceView> out = evidenceService.supersede(T, e.getId(), new SupersedeRequest("Photo was of the wrong tank", photoReq()), ALICE);
        assertEquals(2, out.size());
        assertEquals("SUPERSEDED", out.get(0).status());
        assertEquals("ACTIVE", out.get(1).status());
    }

    @Test @DisplayName("verified evidence cannot be replaced through the service either")
    void supersedeLocked() {
        FuelDeliveryEvidence e = FuelDeliveryEvidence.create(T, UUID.randomUUID(), "PHOTO", "f.jpg", null, null, null, null, ALICE);
        e.verify(BOB);
        when(evidence.findByIdForUpdate(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(e));
        assertThrows(IllegalStateException.class, () -> evidenceService.supersede(T, e.getId(), new SupersedeRequest("Wrong photo really", null), ALICE));
        verify(evidence, never()).save(any());
    }
}
