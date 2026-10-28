package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaxCertificateServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private static final int YEAR = 2027;
    private static final String INTERIM = "INTERIM";

    HrPayRunRepository payRunRepo;
    HrPayslipRepository payslipRepo;
    HrEmp201Repository emp201Repo;
    HrEmployeeRepository employeeRepo;
    HrTaxSettingsRepository settingsRepo;
    HrTaxCertificateRepository certRepo;
    HrTaxCertificateLineRepository lineRepo;
    TaxCertificateService service;

    HrEmployee emp;
    HrPayRun run;
    final List<HrTaxCertificate> saved = new ArrayList<>();
    final List<HrTaxCertificateLine> savedLines = new ArrayList<>();
    long seq = 0;

    @BeforeEach
    void setUp() {
        payRunRepo = mock(HrPayRunRepository.class);
        payslipRepo = mock(HrPayslipRepository.class);
        emp201Repo = mock(HrEmp201Repository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        settingsRepo = mock(HrTaxSettingsRepository.class);
        certRepo = mock(HrTaxCertificateRepository.class);
        lineRepo = mock(HrTaxCertificateLineRepository.class);
        service = new TaxCertificateService(payRunRepo, payslipRepo, emp201Repo, employeeRepo, settingsRepo, certRepo, lineRepo);

        HrTaxSettings settings = HrTaxSettings.of(tenant.getValue());
        settings.update("7123456789", "L123456789", "U123456789", "PENSION", actor);
        when(settingsRepo.findById(any())).thenReturn(Optional.of(settings));

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        ReflectionTestUtils.setField(emp, "idNumber", "8001015009087");
        ReflectionTestUtils.setField(emp, "taxNumber", "0123456789");
        when(employeeRepo.findAllById(any())).thenReturn(List.of(emp));

        run = HrPayRun.create(tenant, "PR-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 25), YEAR);
        run.prepared(new BigDecimal("30000"), new BigDecimal("3000.00"), new BigDecimal("600.00"), new BigDecimal("300.00"), new BigDecimal("26000"), 1, actor);
        ReflectionTestUtils.setField(run, "status", "FINALISED");
        when(payRunRepo.paidBetween(any(), any(), any())).thenReturn(List.of(run));

        HrPayslip slip = HrPayslip.create(tenant, run.getId(), emp.getId(), new BigDecimal("30000"), BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1500"));
        slip.applyCalculations(new BigDecimal("3000.00"), new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00"),
                new BigDecimal("330000"), new BigDecimal("90000"), new BigDecimal("17235"), YEAR);
        slip.setMedicalTaxCredit(new BigDecimal("364.00"));
        when(payslipRepo.findByPayRuns(any())).thenReturn(List.of(slip));

        HrEmp201 emp201 = HrEmp201.create(tenant, run.getId(), run.getPeriodStart(), run.getPeriodEnd(),
                new BigDecimal("3000.00"), new BigDecimal("600.00"), new BigDecimal("300.00"));
        when(emp201Repo.forPayRuns(any(), any())).thenReturn(List.of(emp201));
        when(emp201Repo.coveredRuns(any())).thenReturn(List.of(run.getId()));

        when(certRepo.forReconciliation(any(), any(Integer.class), any())).thenAnswer(i -> saved.stream().filter(c -> c.getTaxYear() == (int) i.getArgument(1) && c.getPeriod().equals(i.getArgument(2))).toList());
        when(certRepo.nextSequence()).thenAnswer(i -> ++seq);
        when(certRepo.save(any())).thenAnswer(i -> { HrTaxCertificate c = i.getArgument(0); if (!saved.contains(c)) saved.add(c); return c; });
        when(certRepo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(lineRepo.save(any())).thenAnswer(i -> { HrTaxCertificateLine l = i.getArgument(0); savedLines.add(l); return l; });
        when(lineRepo.forCertificates(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return savedLines.stream().filter(l -> ids.contains(l.getCertificateId())).toList();
        });
    }

    private void withoutEmp201() { when(emp201Repo.forPayRuns(any(), any())).thenReturn(List.of()); when(emp201Repo.coveredRuns(any())).thenReturn(List.of()); }

    // ── Checks ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void theReconciliationBalancesWhenPayslipsCertificatesAndEmp201Agree() {
        Reconciliation r = service.reconciliation(tenant, YEAR, INTERIM);

        assertThat(r.canIssue()).isTrue();
        assertThat(r.reconciliation()).hasSize(3);
        assertThat(r.reconciliation().stream().allMatch(Recon::balanced)).isTrue();
        assertThat(r.certificates()).hasSize(1);
        assertThat(r.certificates().get(0).certificateType()).isEqualTo("IRP5");
        assertThat(r.certificates().get(0).state()).isEqualTo("NEW");
    }

    @Test
    void theCertificateCarriesTheCodesOfItsPayslips() {
        Certificate c = service.reconciliation(tenant, YEAR, INTERIM).certificates().get(0);

        assertThat(c.lines().stream().map(Line::code).toList()).containsExactly("3601", "4001", "4005", "4102", "4116", "4141", "4142");
        assertThat(c.lines().stream().filter(l -> l.code().equals("4141")).findFirst().orElseThrow().amount()).isEqualByComparingTo("600.00");
    }

    @Test
    void annualLeavePaidOutOnTheLastPayIsReportedAsAnAnnualPayment() {
        HrPayslip last = HrPayslip.create(tenant, run.getId(), emp.getId(), new BigDecimal("30000"), BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1500"));
        last.setLeavePayout(new BigDecimal("5"), new BigDecimal("5769.23"));
        last.applyCalculations(new BigDecimal("3000.00"), new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00"),
                new BigDecimal("330000"), new BigDecimal("90000"), new BigDecimal("17235"), YEAR);
        when(payslipRepo.findByPayRuns(any())).thenReturn(List.of(last));

        Certificate c = service.reconciliation(tenant, YEAR, INTERIM).certificates().get(0);

        assertThat(c.lines().stream().filter(l -> l.code().equals("3601")).findFirst().orElseThrow().amount()).isEqualByComparingTo("30000.00");
        assertThat(c.lines().stream().filter(l -> l.code().equals("3605")).findFirst().orElseThrow().amount()).isEqualByComparingTo("5769.23");
    }

    @Test
    void aRunThatIsNotFinalisedStopsTheCertificates() {
        ReflectionTestUtils.setField(run, "status", "APPROVED");

        Reconciliation r = service.reconciliation(tenant, YEAR, INTERIM);

        assertThat(r.canIssue()).isFalse();
        assertThat(r.problems().stream().anyMatch(p -> p.severity().equals("ERROR") && p.message().contains("Finalise"))).isTrue();
    }

    @Test
    void aMissingEmp201IsAnError() {
        withoutEmp201();

        Reconciliation r = service.reconciliation(tenant, YEAR, INTERIM);

        assertThat(r.canIssue()).isFalse();
        assertThat(r.problems().stream().anyMatch(p -> p.message().contains("No EMP201"))).isTrue();
    }

    @Test
    void anEmp201ThatDiffersFromThePayslipsDoesNotBalance() {
        HrEmp201 off = HrEmp201.create(tenant, run.getId(), run.getPeriodStart(), run.getPeriodEnd(),
                new BigDecimal("2900.00"), new BigDecimal("600.00"), new BigDecimal("300.00"));
        when(emp201Repo.forPayRuns(any(), any())).thenReturn(List.of(off));

        Reconciliation r = service.reconciliation(tenant, YEAR, INTERIM);

        Recon paye = r.reconciliation().stream().filter(x -> x.tax().equals("PAYE")).findFirst().orElseThrow();
        assertThat(paye.balanced()).isFalse();
        assertThat(paye.difference()).isEqualByComparingTo("100.00");
        assertThat(r.canIssue()).isFalse();
    }

    @Test
    void noPayeReferenceStopsIssuing() {
        HrTaxSettings none = HrTaxSettings.of(tenant.getValue());
        when(settingsRepo.findById(any())).thenReturn(Optional.of(none));

        assertThatThrownBy(() -> service.issue(tenant, YEAR, INTERIM, null, actor))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PAYE reference");
        assertThat(saved.isEmpty()).isTrue();
    }

    @Test
    void anEmployeeWithoutAnIdNumberStopsIssuing() {
        ReflectionTestUtils.setField(emp, "idNumber", null);

        assertThatThrownBy(() -> service.issue(tenant, YEAR, INTERIM, null, actor))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ID number");
    }

    @Test
    void aYearNobodyCouldHavePaidInIsRefused() {
        assertThatThrownBy(() -> service.reconciliation(tenant, 1999, INTERIM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reconciliation(tenant, YEAR, "MONTHLY")).isInstanceOf(IllegalArgumentException.class);
    }

    // ── Issuing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void issuingCreatesACertificateWithANumberAndLines() {
        IssueResult res = service.issue(tenant, YEAR, INTERIM, null, actor);

        assertThat(res.issued()).isEqualTo(1);
        HrTaxCertificate c = saved.get(0);
        assertThat(c.getCertificateNumber()).isEqualTo("71234567892027" + "08" + "00000000000001");
        assertThat(c.getCertificateType()).isEqualTo("IRP5");
        assertThat(c.getVersion()).isEqualTo(1);
        assertThat(c.getIdNumber()).isEqualTo("8001015009087");
        assertThat(savedLines.size()).isEqualTo(7);
    }

    @Test
    void issuingAgainWithTheSameFiguresChangesNothing() {
        service.issue(tenant, YEAR, INTERIM, null, actor);

        IssueResult again = service.issue(tenant, YEAR, INTERIM, null, actor);

        assertThat(again.issued()).isEqualTo(0);
        assertThat(again.unchanged()).isEqualTo(1);
        assertThat(saved.size()).isEqualTo(1);
    }

    @Test
    void changedFiguresNeedAReasonAndReplaceTheOldCertificate() {
        service.issue(tenant, YEAR, INTERIM, null, actor);
        HrPayslip changed = HrPayslip.create(tenant, run.getId(), emp.getId(), new BigDecimal("31000"), BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1500"));
        changed.applyCalculations(new BigDecimal("3000.00"), new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00"),
                new BigDecimal("330000"), new BigDecimal("90000"), new BigDecimal("17235"), YEAR);
        changed.setMedicalTaxCredit(new BigDecimal("364.00"));
        when(payslipRepo.findByPayRuns(any())).thenReturn(List.of(changed));

        assertThatThrownBy(() -> service.issue(tenant, YEAR, INTERIM, " ", actor)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");

        IssueResult res = service.issue(tenant, YEAR, INTERIM, "Back pay added", actor);

        assertThat(res.replaced()).isEqualTo(1);
        assertThat(saved.size()).isEqualTo(2);
        assertThat(saved.get(0).getStatus()).isEqualTo("CANCELLED");
        assertThat(saved.get(1).getVersion()).isEqualTo(2);
        assertThat(saved.get(1).getSupersedesId()).isEqualTo(saved.get(0).getId());
        assertThat(saved.get(1).getCertificateNumber()).isEqualTo("71234567892027" + "08" + "00000000000002");
    }

    @Test
    void noOneIssuesWithoutBeingIdentified() {
        assertThatThrownBy(() -> service.issue(tenant, YEAR, INTERIM, null, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ── Cancelling and releasing ────────────────────────────────────────────────────────────────

    @Test
    void cancellingNeedsAReason() {
        service.issue(tenant, YEAR, INTERIM, null, actor);
        UUID id = saved.get(0).getId();
        when(certRepo.findByTenantAndId(any(), any())).thenReturn(Optional.of(saved.get(0)));

        assertThatThrownBy(() -> service.cancel(tenant, id, " ", actor)).isInstanceOf(IllegalArgumentException.class);
        service.cancel(tenant, id, "Wrong employee", actor);
        assertThat(saved.get(0).getStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service.cancel(tenant, id, "Again", actor)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyIssuedAnnualCertificatesCanBeReleased() {
        service.issue(tenant, YEAR, INTERIM, null, actor);

        // Nothing annual has been issued, so there is nothing to release.
        assertThatThrownBy(() -> service.release(tenant, YEAR, actor)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasingMakesAnAnnualCertificateAvailable() {
        HrPayRun annualRun = run;
        ReflectionTestUtils.setField(annualRun, "payDate", LocalDate.of(2027, 2, 25));
        service.issue(tenant, YEAR, "ANNUAL", null, actor);

        int released = service.release(tenant, YEAR, actor);

        assertThat(released).isEqualTo(1);
        assertThat(saved.get(0).getReleasedAt() != null).isTrue();
        assertThatThrownBy(() -> service.release(tenant, YEAR, actor)).isInstanceOf(IllegalStateException.class);
    }

    // ── Export ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void theSpreadsheetDefusesNamesThatLookLikeFormulas() {
        ReflectionTestUtils.setField(emp, "lastName", "=HYPERLINK(\"http://evil\")");
        service.issue(tenant, YEAR, INTERIM, null, actor);
        when(certRepo.forReconciliation(any(), any(Integer.class), any())).thenReturn(saved);

        String csv = service.csv(tenant, YEAR, INTERIM);

        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(csv.split("\r\n").length).isEqualTo(1 + 7);
    }

    @Test
    void previewingIssuesNothingAndLeavesOutTotalsThatWereNotVerified() {
        Reconciliation r = service.reconciliation(tenant, YEAR, INTERIM);

        assertThat(r.notProduced().isEmpty()).isFalse();
        for (Certificate c : r.certificates())
            assertThat(c.lines().stream().noneMatch(l -> l.code().equals("3699") || l.code().equals("4149"))).isTrue();
        ArgumentCaptor<HrTaxCertificate> none = ArgumentCaptor.forClass(HrTaxCertificate.class);
        verify(certRepo, never()).save(none.capture());
    }
}
