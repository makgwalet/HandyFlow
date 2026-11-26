package za.co.handyflow.platform.payrollbureau.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.payrollbureau.domain.model.*;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayslipDeliveryServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final TenantId TENANT = TenantId.of(TENANT_ID);

    @Mock private PayRunRepository payRunRepo;
    @Mock private PayslipRepository payslipRepo;
    @Mock private PayEmployeeRepository employeeRepo;
    @Mock private PayClientRepository clientRepo;
    @Mock private PayslipDeliveryRepository deliveryRepo;
    @Mock private PayBureauPayslipPdfGenerator pdf;
    @Mock private za.co.handyflow.platform.shared.EmailService emailService;
    @InjectMocks private PayrollBureauService service;

    private final UUID runId = UUID.randomUUID();

    private Payslip payslip(UUID employeeId) {
        Payslip p = mock(Payslip.class);
        lenient().when(p.getId()).thenReturn(UUID.randomUUID());
        lenient().when(p.getPayRunId()).thenReturn(runId);
        lenient().when(p.getPayEmployeeId()).thenReturn(employeeId);
        lenient().when(p.getNetPay()).thenReturn(new BigDecimal("1000"));
        return p;
    }

    private PayEmployee employee(UUID id, String email) {
        PayEmployee e = mock(PayEmployee.class);
        lenient().when(e.getId()).thenReturn(id);
        lenient().when(e.getFirstName()).thenReturn("Thandi");
        lenient().when(e.getLastName()).thenReturn("Nkosi");
        lenient().when(e.getEmail()).thenReturn(email);
        return e;
    }

    private void givenRun(List<Payslip> slips, List<PayslipDelivery> existing) {
        PayRun run = PayRun.create(TENANT_ID, UUID.randomUUID(), "PR00001",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 3, 31), 2027);
        when(payRunRepo.findByTenantAndId(TENANT_ID, runId)).thenReturn(Optional.of(run));
        PayClient client = mock(PayClient.class);
        when(clientRepo.findActiveById(eq(TENANT_ID), any())).thenReturn(Optional.of(client));
        when(payslipRepo.findByPayRun(TENANT_ID, runId)).thenReturn(slips);
        when(deliveryRepo.findByRun(TENANT_ID, runId)).thenReturn(existing);
    }

    @Test
    void oneFailureIsRecordedAndTheOthersStillGo() {
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();
        Payslip p1 = payslip(e1), p2 = payslip(e2);
        givenRun(List.of(p1, p2), List.of());
        when(employeeRepo.findActiveById(TENANT_ID, e1)).thenReturn(Optional.of(employee(e1, "a@x.co.za")));
        when(employeeRepo.findActiveById(TENANT_ID, e2)).thenReturn(Optional.of(employee(e2, "b@x.co.za")));
        doThrow(new RuntimeException("mailbox full")).when(emailService)
                .sendWithAttachment(eq("a@x.co.za"), anyString(), anyString(), anyString(), any());

        var result = service.emailPayslips(TENANT, runId);

        assertEquals(1, result.sent());
        assertEquals(1, result.failed());
        assertEquals(1, result.stillRetryable());
        verify(deliveryRepo, times(2)).save(any(PayslipDelivery.class));
    }

    @Test
    void aPayslipAlreadySentIsNotMailedAgain() {
        UUID e1 = UUID.randomUUID();
        Payslip p1 = payslip(e1);
        PayslipDelivery done = PayslipDelivery.forPayslip(TENANT_ID, p1);
        done.sent("a@x.co.za");
        givenRun(List.of(p1), List.of(done));

        var result = service.emailPayslips(TENANT, runId);

        assertEquals(0, result.sent());
        assertEquals(1, result.alreadySent());
        verify(emailService, never()).sendWithAttachment(any(), any(), any(), any(), any());
    }

    @Test
    void theDailyJobStopsRetryingAfterTheLimit() {
        UUID e1 = UUID.randomUUID();
        Payslip p1 = payslip(e1);
        PayslipDelivery bad = PayslipDelivery.forPayslip(TENANT_ID, p1);
        for (int i = 0; i < 3; i++) bad.failed("a@x.co.za", "boom");
        givenRun(List.of(p1), List.of(bad));
        lenient().when(employeeRepo.findActiveById(TENANT_ID, e1)).thenReturn(Optional.of(employee(e1, "a@x.co.za")));

        var result = service.emailPayslips(TENANT, runId, false, true);

        assertEquals(0, result.stillRetryable());
        assertEquals(1, result.failed());
        verify(emailService, never()).sendWithAttachment(any(), any(), any(), any(), any());
    }
}
