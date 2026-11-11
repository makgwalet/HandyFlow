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
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftAdjustment;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftAdjustmentRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentRequest;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelCashControlServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID OPENER = UUID.randomUUID(), SUPERVISOR = UUID.randomUUID();

    @Mock FuelShiftAdjustmentRepository adjustments;
    @Mock FuelShiftRepository shifts;
    @Mock FuelSettingsService settings;
    @InjectMocks FuelCashControlService service;

    FuelShift shift;
    UUID shiftId;

    @BeforeEach
    void setUp() {
        shift = FuelShift.open(T, "SH-1", "Thabo", OPENER, null);
        shiftId = shift.getId();
        when(shifts.findByTenantAndId(T, shiftId)).thenReturn(Optional.of(shift));
        when(adjustments.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private FuelShiftAdjustment stored(String kind) {
        AdjustmentView v = service.record(T, shiftId, new AdjustmentRequest(kind, new BigDecimal("50"), "Customer overfilled"), OPENER);
        UUID id = v.id();
        FuelShiftAdjustment a = FuelShiftAdjustment.record(T, shiftId, kind, new BigDecimal("50"), "Customer overfilled", OPENER);
        when(adjustments.findByIdForUpdate(eq(T), any())).thenReturn(Optional.of(a));
        assertNotNull(id);
        return a;
    }

    @Test @DisplayName("policy reads the company's tolerance and separate-closer rule")
    void policyFromSettings() {
        FuelSettings s = FuelSettings.forTenant(T);
        s.updateCashControls(new BigDecimal("12.50"), true);
        when(settings.current(T)).thenReturn(s);
        FuelCashControlService.CashPolicy p = service.policy(T);
        assertEquals(0, new BigDecimal("12.50").compareTo(p.tolerance()));
        assertTrue(p.requireSeparateCloser());
    }

    @Test @DisplayName("an adjustment is recorded as pending on an open shift")
    void recordsPending() {
        AdjustmentView v = service.record(T, shiftId, new AdjustmentRequest("REFUND", new BigDecimal("50"), "Customer overfilled"), OPENER);
        assertEquals("PENDING", v.status());
        assertEquals(shiftId, v.shiftId());
    }

    @Test @DisplayName("nothing can be recorded on a closed shift")
    void refusedOnClosedShift() {
        shift.close(new BigDecimal("0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, SUPERVISOR, new BigDecimal("5"), false);
        assertThrows(IllegalStateException.class, () -> service.record(T, shiftId, new AdjustmentRequest("VOID", new BigDecimal("5"), "Wrong pump used"), OPENER));
    }

    @Test @DisplayName("an unknown shift is a 404")
    void unknownShift() {
        assertThrows(ResourceNotFoundException.class, () -> service.record(T, UUID.randomUUID(), new AdjustmentRequest("VOID", new BigDecimal("5"), "Wrong pump used"), OPENER));
    }

    @Test @DisplayName("the recorder cannot approve their own entry; a supervisor can")
    void fourEyes() {
        FuelShiftAdjustment a = stored("REFUND");
        UUID id = a.getId();
        assertThrows(IllegalStateException.class, () -> service.approve(T, id, null, OPENER));
        assertEquals("APPROVED", service.approve(T, id, "Seen on CCTV", SUPERVISOR).status());
    }

    @Test @DisplayName("approval is refused once the shift has closed")
    void noApprovalAfterClose() {
        FuelShiftAdjustment a = stored("DISCOUNT");
        UUID id = a.getId();
        shift.close(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, SUPERVISOR, new BigDecimal("5"), false);
        assertThrows(IllegalStateException.class, () -> service.approve(T, id, null, SUPERVISOR));
    }

    @Test @DisplayName("rejecting needs a reason")
    void rejectNeedsReason() {
        FuelShiftAdjustment a = stored("VOID");
        UUID id = a.getId();
        assertThrows(IllegalArgumentException.class, () -> service.reject(T, id, "no", SUPERVISOR));
        assertEquals("REJECTED", service.reject(T, id, "No receipt for this", SUPERVISOR).status());
    }

    @Test @DisplayName("reviewing a shift saves it as reviewed, by someone other than the closer")
    void reviewShift() {
        shift.close(new BigDecimal("0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, "Drive-off reported", OPENER, new BigDecimal("5"), false);
        assertThrows(IllegalStateException.class, () -> service.reviewShift(T, shiftId, "Looked into it", OPENER));
        service.reviewShift(T, shiftId, "Looked into it", SUPERVISOR);
        assertEquals("REVIEWED", shift.getReviewStatus());
        verify(shifts, times(1)).save(shift);
    }

    @Test @DisplayName("approved total is zero, not null, when there are none")
    void approvedTotalNullSafe() {
        when(adjustments.sumApproved(T, shiftId)).thenReturn(null);
        assertEquals(0, BigDecimal.ZERO.compareTo(service.approvedTotal(T, shiftId)));
    }
}
