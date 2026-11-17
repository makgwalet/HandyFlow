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
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheck;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryCompartment;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Severity;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelArrivalCheckServiceTest {

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankerCompartmentRepository compartments;
    @Mock FuelDeliveryCompartmentRepository loadSplits;
    @Mock FuelArrivalCheckRepository checks;
    @Mock FuelEligibilityService eligibility;
    @Mock HrFacade hr;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    FuelArrivalCheckService service;
    EmployeeSummary me;
    FuelDelivery load;
    FuelTanker tanker;

    @BeforeEach
    void setUp() {
        service = new FuelArrivalCheckService(deliveries, tankers, compartments, loadSplits, checks, eligibility, hr, new ObjectMapper());
        me = new EmployeeSummary(UUID.randomUUID(), "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE");
        tanker = FuelTanker.create(T, "CA 123-456", "Tanker 1");
        load = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", new BigDecimal("1000"), new BigDecimal("20"), Instant.now(), null, null);
        load.allocate(tanker.getId(), "CA 123-456", me.id(), "Moses Sithole");
        when(hr.findEmployeeByUserId(T, USER)).thenReturn(Optional.of(me));
        when(deliveries.findActiveById(eq(T), any())).thenReturn(Optional.of(load));
        when(tankers.findActiveById(eq(T), any())).thenReturn(Optional.of(tanker));
        when(loadSplits.findByDelivery(eq(T), any())).thenReturn(List.of(FuelDeliveryCompartment.create(T, load.getId(), UUID.randomUUID(), new BigDecimal("1000"))));
        when(checks.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void result(Finding... f) {
        when(eligibility.evaluate(eq(T), any(), any(), any(), any(), any(), any())).thenReturn(new EligibilityResult(List.of(f)));
    }

    @Test @DisplayName("a clean check is allowed, and the result and time are recorded")
    void cleanCheckIsRecorded() {
        result();
        ArrivalCheckView v = service.check(T, USER, load.getId());
        assertTrue(v.allowed());
        assertNotNull(v.checkedAt());
        ArgumentCaptor<FuelArrivalCheck> saved = ArgumentCaptor.forClass(FuelArrivalCheck.class);
        verify(checks).save(saved.capture());
        assertEquals(USER, saved.getValue().getCheckedBy());
        assertTrue(saved.getValue().isAllowed());
    }

    @Test @DisplayName("a blocking finding is not allowed, and is shown as found")
    void blockedCheck() {
        result(new Finding("TANKER_NOT_APPROVED", Severity.BLOCK, "Tanker is not approved at this site."));
        ArrivalCheckView v = service.check(T, USER, load.getId());
        assertFalse(v.allowed());
        assertEquals("TANKER_NOT_APPROVED", v.findings().get(0).code());
    }

    @Test @DisplayName("a review point alone does not block the driver")
    void reviewDoesNotBlock() {
        result(new Finding("CLEANING_AGE", Severity.REVIEW, "Cleaning certificate is old."));
        assertTrue(service.check(T, USER, load.getId()).allowed());
    }

    @Test @DisplayName("another driver's load is reported as not found")
    void notYourLoad() {
        load.allocate(tanker.getId(), "CA 123-456", UUID.randomUUID(), "Someone Else");
        assertThrows(ResourceNotFoundException.class, () -> service.check(T, USER, load.getId()));
        verify(checks, never()).save(any());
    }

    @Test @DisplayName("a login with no employee record gets a clear message")
    void noEmployee() {
        when(hr.findEmployeeByUserId(T, USER)).thenReturn(Optional.empty());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.check(T, USER, load.getId()));
        assertTrue(e.getMessage().contains("not linked"));
    }

    @Test @DisplayName("the latest check comes back with its findings")
    void latest() {
        result(new Finding("X", Severity.INFO, "Note"));
        service.check(T, USER, load.getId());
        ArgumentCaptor<FuelArrivalCheck> saved = ArgumentCaptor.forClass(FuelArrivalCheck.class);
        verify(checks).save(saved.capture());
        when(checks.latest(eq(T), any(), any())).thenReturn(List.of(saved.getValue()));
        ArrivalCheckView v = service.latest(T, USER, load.getId()).orElseThrow();
        assertEquals("X", v.findings().get(0).code());
        when(checks.latest(eq(T), any(), any())).thenReturn(List.of());
        assertTrue(service.latest(T, USER, load.getId()).isEmpty());
    }
}
