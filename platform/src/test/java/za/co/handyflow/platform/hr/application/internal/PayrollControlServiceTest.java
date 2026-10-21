package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PayRunResponse;
import za.co.handyflow.platform.hr.dto.PayRunReviewResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Approve, send back and finalise: who may, what is written down, and what blocks approval. */
class PayrollControlServiceTest {

    private HrPayRunRepository payRunRepo;
    private HrPayslipRepository payslipRepo;
    private HrEmployeeRepository employeeRepo;
    private HrPayRunEventRepository eventRepo;
    private HrPayrollSettingsRepository settingsRepo;
    private MedicalSchemeService medical;
    private PayrollControlService service;

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();
    private HrPayRun run;

    @BeforeEach
    void setUp() {
        payRunRepo = mock(HrPayRunRepository.class);
        payslipRepo = mock(HrPayslipRepository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        eventRepo = mock(HrPayRunEventRepository.class);
        settingsRepo = mock(HrPayrollSettingsRepository.class);
        medical = mock(MedicalSchemeService.class);
        service = new PayrollControlService(payRunRepo, payslipRepo, employeeRepo, eventRepo, settingsRepo, mock(HrEmployeeChangeRepository.class), mock(HrLeaveRequestRepository.class), medical, mock(JdbcTemplate.class));

        run = HrPayRun.create(tenant, "PR00002", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 25), 2027);
        run.prepared(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, 1, maker);
        when(payRunRepo.findByTenantAndId(any(), eq(run.getId()))).thenReturn(Optional.of(run));
        when(payRunRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payRunRepo.previousIssued(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(settingsRepo.findById(any())).thenReturn(Optional.empty());
        when(medical.membersOn(any(), any(), any())).thenReturn(Map.of());
        when(employeeRepo.findAllById(any())).thenReturn(List.of());
        slips("20000.00");
    }

    private void slips(String net) {
        HrPayslip s = mock(HrPayslip.class);
        when(s.getEmployeeId()).thenReturn(UUID.randomUUID());
        when(s.getNetPay()).thenReturn(new BigDecimal(net));
        when(payslipRepo.findByPayRun(run.getId())).thenReturn(List.of(s));
    }

    @Test
    void anotherPersonApprovesAndTheStepIsRecorded() {
        PayRunResponse r = service.approve(tenant, run.getId(), checker, "Checked against timesheets");
        assertThat(r.status()).isEqualTo("APPROVED");
        assertThat(r.approvedBy()).isEqualTo(checker);
        verify(eventRepo).save(argThat((HrPayRunEvent e) -> e.getAction().equals("APPROVED") && checker.equals(e.getActorId())
                && "Checked against timesheets".equals(e.getNote())));
    }

    @Test
    void thePreparerIsRefusedAndNothingIsWritten() {
        assertThatThrownBy(() -> service.approve(tenant, run.getId(), maker, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(run.getStatus()).isEqualTo("PREPARED");
        verify(eventRepo, never()).save(any());
        verify(payRunRepo, never()).save(any());
    }

    @Test
    void whenTheBusinessAllowsItTheOwnRunCanBeApprovedAndTheHistorySaysSo() {
        HrPayrollSettings s = HrPayrollSettings.create(tenant.getValue());
        s.setAllowSelfApproval(true, checker);
        when(settingsRepo.findById(any())).thenReturn(Optional.of(s));
        service.approve(tenant, run.getId(), maker, null);
        verify(eventRepo).save(argThat((HrPayRunEvent e) -> e.getAction().equals("APPROVED_OWN_RUN") && e.getNote().contains("allowed by this business")));
    }

    @Test
    void aZeroNetPayslipBlocksApproval() {
        slips("0.00");
        assertThatThrownBy(() -> service.approve(tenant, run.getId(), checker, null)).hasMessageContaining("errors");
        assertThat(run.getStatus()).isEqualTo("PREPARED");
        verify(eventRepo, never()).save(any());
    }

    @Test
    void reviewListsTheErrorAndSaysItBlocks() {
        slips("-1.00");
        PayRunReviewResponse r = service.review(tenant, run.getId());
        assertThat(r.blocking()).isTrue();
        assertThat(r.errors()).isEqualTo(1);
        assertThat(r.items().get(0).code()).isEqualTo("NET_NOT_POSITIVE");
    }

    @Test
    void reviewComparesWithThePreviousIssuedRun() {
        HrPayRun earlier = HrPayRun.create(tenant, "PR00001", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 25), 2027);
        when(payRunRepo.previousIssued(any(), any(), any(Pageable.class))).thenReturn(List.of(earlier));
        when(payslipRepo.findByPayRun(earlier.getId())).thenReturn(List.of());
        assertThat(service.review(tenant, run.getId()).comparedWith()).isEqualTo("PR00001");
    }

    @Test
    void sendingBackNeedsAReasonAndClearsTheApproval() {
        run.approve(checker);
        assertThatThrownBy(() -> service.sendBack(tenant, run.getId(), checker, " ")).isInstanceOf(IllegalArgumentException.class);
        PayRunResponse r = service.sendBack(tenant, run.getId(), checker, "Overtime missing for two people");
        assertThat(r.status()).isEqualTo("DRAFT");
        assertThat(r.approvedBy()).isNull();
        verify(eventRepo).save(argThat((HrPayRunEvent e) -> e.getAction().equals("SENT_BACK") && e.getFromStatus().equals("APPROVED")));
    }

    @Test
    void finalisingLocksAnApprovedRun() {
        run.approve(checker);
        PayRunResponse r = service.finalise(tenant, run.getId(), checker, null);
        assertThat(r.status()).isEqualTo("FINALISED");
        assertThat(r.finalisedBy()).isEqualTo(checker);
        assertThatThrownBy(() -> service.sendBack(tenant, run.getId(), checker, "Too late")).hasMessageContaining("locked");
    }

    @Test
    void aRunThatIsNotApprovedCannotBeFinalised() {
        assertThatThrownBy(() -> service.finalise(tenant, run.getId(), checker, null)).hasMessageContaining("approved");
    }

    @Test
    void thePreparerCannotFinaliseEither() {
        run.approve(checker);
        assertThatThrownBy(() -> service.finalise(tenant, run.getId(), maker, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void settingsDefaultToNobodyApprovingTheirOwnWork() {
        assertThat(service.settings(tenant).allowSelfApproval()).isFalse();
        assertThat(service.settings(tenant).legacyAccessEnds()).isEqualTo(PayrollAccess.LEGACY_ACCESS_ENDS);
    }

    @Test
    void settingsAreSavedWithWhoChangedThem() {
        service.updateSettings(tenant, true, checker);
        verify(settingsRepo).save(argThat((HrPayrollSettings s) -> s.isAllowSelfApproval() && checker.equals(s.getUpdatedBy())));
    }

    @Test
    void aRunOfAnotherTenantIsNotFound() {
        assertThatThrownBy(() -> service.approve(tenant, UUID.randomUUID(), checker, null)).hasMessageContaining("PayRun");
    }
}
