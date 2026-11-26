package za.co.handyflow.platform.payrollbureau.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.payrollbureau.domain.model.PayGroup;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayGroupRunTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final TenantId TENANT = TenantId.of(TENANT_ID);

    @Mock private PayRunRepository payRunRepo;
    @Mock private PayslipRepository payslipRepo;
    @Mock private PayEmployeeRepository employeeRepo;
    @Mock private PayrollBureauEngine engine;
    @Mock private PayGroupService groupService;
    @InjectMocks private PayrollBureauService service;

    private final UUID clientId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    private PayRun groupRun() {
        return PayRun.createForGroup(TENANT_ID, clientId, groupId, "PR00001",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 7), LocalDate.of(2027, 3, 9), 2028);
    }

    @Test
    void aWeeklyGroupsRunIsRefusedUntilPeriodicPayeExists() {
        PayRun run = groupRun();
        when(payRunRepo.findByTenantAndId(TENANT_ID, run.getId())).thenReturn(Optional.of(run));
        when(engine.requireRules(2028)).thenReturn(mock(StatutoryYearRules.class));
        PayGroup g = PayGroup.create(TENANT_ID, clientId, "Factory", "WEEKLY", LocalDate.of(2026, 3, 2), 2);
        when(groupService.load(TENANT, groupId)).thenReturn(g);

        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.processPayRun(TENANT, run.getId()));

        assertTrue(e.getMessage().contains("cannot be processed yet"));
        assertEquals("DRAFT", run.getStatus());
        verify(payslipRepo, never()).save(any());
    }

    @Test
    void aRunWithoutAGroupOnlyPaysUngroupedEmployees() {
        PayRun run = PayRun.create(TENANT_ID, clientId, "PR00002",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 3, 31), 2027);
        when(payRunRepo.findByTenantAndId(TENANT_ID, run.getId())).thenReturn(Optional.of(run));
        when(engine.requireRules(2027)).thenReturn(mock(StatutoryYearRules.class));
        when(employeeRepo.findActiveUngrouped(clientId)).thenReturn(List.<PayEmployee>of());
        when(employeeRepo.sumMonthlyGrossByClient(clientId)).thenReturn(BigDecimal.ZERO);

        service.processPayRun(TENANT, run.getId());

        verify(employeeRepo).findActiveUngrouped(clientId);
        verify(employeeRepo, never()).findActiveByClient(any());
        verify(employeeRepo, never()).findActiveByGroup(any(), any());
    }
}
