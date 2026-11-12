package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelAutomationLog;
import za.co.handyflow.platform.fuel.domain.model.FuelAutomationRule;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.RuleRequest;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.RuleView;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.RunResult;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionView;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 6b: a rule drafts a requisition when a tank is below the company's own level, never doubles up, and never submits or orders. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelAutomationServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    @Mock FuelAutomationRuleRepository rules;
    @Mock FuelAutomationLogRepository logs;
    @Mock FuelTankRepository tanks;
    @Mock FuelSupplierRepository suppliers;
    @Mock FuelPurchaseRequisitionRepository requisitions;
    @Mock FuelPurchaseOrderRepository orders;
    @Mock FuelRequisitionService requisitionService;
    @Mock FuelSettingsService settings;
    @InjectMocks FuelAutomationService service;

    FuelTank tank;
    UUID tankId = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelAutomationRule rule(String below, String qty) { return FuelAutomationRule.create(T, tankId, null, bd(below), bd(qty), bd("21.50"), USER); }

    private RequisitionView drafted(String number) {
        return new RequisitionView(UUID.randomUUID(), number, "DRAFT", "DIESEL_50", bd("5000"), tankId, null, null, bd("21.50"), bd("107500"), null, "r", null, null, null, null, null, Instant.now());
    }

    @BeforeEach
    void setUp() {
        tank = mock(FuelTank.class);
        when(tank.getId()).thenReturn(tankId); when(tank.getName()).thenReturn("Tank 1"); when(tank.getFuelType()).thenReturn("DIESEL_50");
        when(tank.getCurrentLitres()).thenReturn(bd("1500"));
        when(tanks.findActiveById(T, tankId)).thenReturn(Optional.of(tank));
        when(tanks.findAllActive(T)).thenReturn(List.of(tank));
        when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
        when(requisitionService.createFromRule(eq(T), any(), any())).thenReturn(drafted("REQ-2026-00001"));
    }

    @Test @DisplayName("below the level: one DRAFT requisition is made with the rule's litres, supplier price and a reason that names the tank and the levels; the run is logged")
    void draftsWhenBelow() {
        FuelAutomationRule r = rule("2000", "5000");
        when(rules.findEnabled(T)).thenReturn(List.of(r));
        RunResult res = service.runRules(T);
        assertEquals(1, res.drafted()); assertEquals(List.of("REQ-2026-00001"), res.requisitionNumbers());
        ArgumentCaptor<RequisitionRequest> cap = ArgumentCaptor.forClass(RequisitionRequest.class);
        verify(requisitionService).createFromRule(eq(T), cap.capture(), eq(r.getId()));
        assertEquals(0, bd("5000").compareTo(cap.getValue().litres())); assertEquals("DIESEL_50", cap.getValue().fuelType()); assertEquals(tankId, cap.getValue().tankId());
        assertTrue(cap.getValue().reason().contains("Tank 1") && cap.getValue().reason().contains("1500") && cap.getValue().reason().contains("2000"));
        ArgumentCaptor<FuelAutomationLog> log = ArgumentCaptor.forClass(FuelAutomationLog.class);
        verify(logs).save(log.capture()); assertEquals("DRAFTED", log.getValue().getOutcome());
        assertNotNull(r.getLastDraftedAt());
    }

    @Test @DisplayName("it only ever drafts: the requisition service is never asked to submit, convert or cancel")
    void neverSubmits() {
        when(rules.findEnabled(T)).thenReturn(List.of(rule("2000", "5000")));
        service.runRules(T);
        verify(requisitionService, never()).submit(any(), any(), any());
        verify(requisitionService, never()).convert(any(), any(), any(), any());
        verify(requisitionService, never()).create(any(), any(), any());
    }

    @Test @DisplayName("at or above the level nothing is drafted")
    void notBelow() {
        when(tank.getCurrentLitres()).thenReturn(bd("2000"));
        when(rules.findEnabled(T)).thenReturn(List.of(rule("2000", "5000")));
        RunResult res = service.runRules(T);
        assertEquals(0, res.drafted()); assertEquals(1, res.checked());
        verify(requisitionService, never()).createFromRule(any(), any(), any());
    }

    @Test @DisplayName("a tank that already has a requisition moving, or an order on the way, is not drafted again")
    void noDoubling() {
        when(rules.findEnabled(T)).thenReturn(List.of(rule("2000", "5000")));
        when(requisitions.countOpenForTank(T, tankId)).thenReturn(1L);
        RunResult a = service.runRules(T);
        assertEquals(0, a.drafted()); assertEquals(1, a.alreadyOpen());
        when(requisitions.countOpenForTank(T, tankId)).thenReturn(0L);
        when(requisitions.countOnOrderForTank(T, tankId)).thenReturn(1L);
        RunResult b = service.runRules(T);
        assertEquals(0, b.drafted()); assertEquals(1, b.alreadyOpen());
        verify(requisitionService, never()).createFromRule(any(), any(), any());
    }

    @Test @DisplayName("one rule failing is logged and does not stop the next rule")
    void failureIsolated() {
        UUID tank2Id = UUID.randomUUID();
        FuelTank tank2 = mock(FuelTank.class);
        when(tank2.getId()).thenReturn(tank2Id); when(tank2.getName()).thenReturn("Tank 2"); when(tank2.getFuelType()).thenReturn("DIESEL_50"); when(tank2.getCurrentLitres()).thenReturn(bd("10"));
        when(tanks.findActiveById(T, tank2Id)).thenReturn(Optional.of(tank2));
        FuelAutomationRule bad = rule("2000", "5000"), good = FuelAutomationRule.create(T, tank2Id, null, bd("100"), bd("1000"), null, USER);
        when(rules.findEnabled(T)).thenReturn(List.of(bad, good));
        when(requisitionService.createFromRule(eq(T), any(), eq(bad.getId()))).thenThrow(new IllegalStateException("Supplier is suspended"));
        RunResult res = service.runRules(T);
        assertEquals(1, res.failed()); assertEquals(1, res.drafted());
        ArgumentCaptor<FuelAutomationLog> log = ArgumentCaptor.forClass(FuelAutomationLog.class);
        verify(logs, times(2)).save(log.capture());
        assertTrue(log.getAllValues().stream().anyMatch(l -> "FAILED".equals(l.getOutcome()) && l.getDetail().contains("suspended")));
    }

    @Test @DisplayName("a removed tank is skipped quietly")
    void removedTank() {
        when(tanks.findActiveById(T, tankId)).thenReturn(Optional.empty());
        when(rules.findEnabled(T)).thenReturn(List.of(rule("2000", "5000")));
        assertEquals(0, service.runRules(T).drafted());
    }

    @Test @DisplayName("one rule per tank; an unknown tank or supplier is a 404; bad numbers are refused")
    void createRules() {
        when(rules.findActiveByTank(T, tankId)).thenReturn(Optional.of(rule("1", "1")));
        assertThrows(ConflictException.class, () -> service.create(T, new RuleRequest(tankId, null, bd("2000"), bd("5000"), null, null), USER));
        when(rules.findActiveByTank(T, tankId)).thenReturn(Optional.empty());
        UUID stranger = UUID.randomUUID();
        when(suppliers.findActiveById(T, stranger)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, new RuleRequest(tankId, stranger, bd("2000"), bd("5000"), null, null), USER));
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, new RuleRequest(UUID.randomUUID(), null, bd("2000"), bd("5000"), null, null), USER));
        assertThrows(IllegalArgumentException.class, () -> service.create(T, new RuleRequest(tankId, null, bd("0"), bd("5000"), null, null), USER));
        RuleView v = service.create(T, new RuleRequest(tankId, null, bd("2000"), bd("5000"), null, null), USER);
        assertTrue(v.enabled()); assertTrue(v.belowLevel()); assertEquals("Tank 1", v.tankName());
        RuleView off = service.create(T, new RuleRequest(tankId, null, bd("2000"), bd("5000"), null, false), USER);
        assertFalse(off.enabled());
    }

    @Test @DisplayName("a level exactly at the tank's litres is not below; a null reading is not below")
    void belowIsStrict() {
        FuelAutomationRule r = rule("2000", "5000");
        assertFalse(r.isBelow(bd("2000"))); assertTrue(r.isBelow(bd("1999.99"))); assertFalse(r.isBelow(null));
    }
}
