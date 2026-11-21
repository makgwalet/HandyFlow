package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security audit 1.6 through SecurityPayrollService: no shift is paid twice, a branch period pays only its branch, running shifts block approval, deleted guards are paid,
 * and a repeat export works. NOT RUN by the author (no Maven in the authoring sandbox): `mvn -q test -Dtest=SecurityPayrollServiceTest`.
 */
class SecurityPayrollServiceTest {

    private static final TenantId T = TenantId.generate();
    private final PayrollPeriodRepository periods = mock(PayrollPeriodRepository.class);
    private final PayrollLineItemRepository lines = mock(PayrollLineItemRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final GuardRepository guards = mock(GuardRepository.class);
    private final GradeRateRepository rates = mock(GradeRateRepository.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final SecurityPayrollService service = new SecurityPayrollService(periods, lines, shifts, guards, rates, mock(BranchRepository.class), sites, mock(AuditEventRepository.class));

    private final UUID approver = UUID.randomUUID(), guardId = UUID.randomUUID(), siteA = UUID.randomUUID(), siteB = UUID.randomUUID();

    private PayrollPeriod period(UUID branch) {
        PayrollPeriod p = PayrollPeriod.create(T, branch, "Nov 2026", PayrollPeriod.PeriodType.MONTHLY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), approver);
        when(periods.findByTenantAndId(T, p.getId())).thenReturn(Optional.of(p));
        return p;
    }
    private Shift shift(ShiftStatus status, UUID site) {
        Shift s = mock(Shift.class);
        when(s.getId()).thenReturn(UUID.randomUUID());
        when(s.getStatus()).thenReturn(status);
        when(s.getGuardId()).thenReturn(guardId);
        when(s.getSiteId()).thenReturn(site);
        when(s.getStartAt()).thenReturn(Instant.parse("2026-11-10T04:00:00Z"));
        when(s.getEndAt()).thenReturn(Instant.parse("2026-11-10T12:00:00Z"));
        return s;
    }
    private Guard guard() {
        Guard g = mock(Guard.class);
        when(g.getId()).thenReturn(guardId);
        when(g.getHourlyRateCents()).thenReturn(3000);
        when(guards.findAnyById(T, guardId)).thenReturn(Optional.of(g));
        return g;
    }
    @SuppressWarnings("unchecked")
    private List<PayrollLineItem> saved() {
        ArgumentCaptor<List<PayrollLineItem>> c = ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(c.capture());
        return c.getValue();
    }

    @Test @DisplayName("a shift that already has pay in another period is not paid again; the others are")
    void alreadyPaidShiftIsSkipped() {
        PayrollPeriod p = period(null);
        Shift paid = shift(ShiftStatus.COMPLETED, siteA), fresh = shift(ShiftStatus.COMPLETED, siteA);
        when(shifts.findByTenantInRange(any(), any(), any())).thenReturn(List.of(paid, fresh));
        when(lines.findPaidShiftIds(any(), any())).thenReturn(List.of(paid.getId()));
        guard();
        service.approvePeriod(T, p.getId(), approver);
        assertThat(saved()).extracting(PayrollLineItem::getShiftId).containsOnly(fresh.getId());
    }

    @Test @DisplayName("if every shift is already paid there is nothing to approve, so nothing is saved")
    void allPaid() {
        PayrollPeriod p = period(null);
        Shift paid = shift(ShiftStatus.COMPLETED, siteA);
        when(shifts.findByTenantInRange(any(), any(), any())).thenReturn(List.of(paid));
        when(lines.findPaidShiftIds(any(), any())).thenReturn(List.of(paid.getId()));
        guard();
        assertThatThrownBy(() -> service.approvePeriod(T, p.getId(), approver)).isInstanceOf(HandyFlowException.class).hasMessageContaining("No completed shifts");
        verify(lines, never()).saveAll(any());
    }

    @Test @DisplayName("a branch period pays only shifts at that branch's sites")
    void branchPeriod() {
        UUID branch = UUID.randomUUID();
        PayrollPeriod p = period(branch);
        Shift mine = shift(ShiftStatus.COMPLETED, siteA), other = shift(ShiftStatus.COMPLETED, siteB);
        when(sites.findIdsByBranch(T, branch)).thenReturn(List.of(siteA));
        when(shifts.findByTenantInRange(any(), any(), any())).thenReturn(List.of(mine, other));
        when(lines.findPaidShiftIds(any(), any())).thenReturn(List.of());
        guard();
        service.approvePeriod(T, p.getId(), approver);
        assertThat(saved()).extracting(PayrollLineItem::getShiftId).containsOnly(mine.getId());
    }

    @Test @DisplayName("a shift still running blocks approval and nothing is saved")
    void runningShiftBlocks() {
        PayrollPeriod p = period(null);
        when(shifts.findByTenantInRange(any(), any(), any())).thenReturn(List.of(shift(ShiftStatus.COMPLETED, siteA), shift(ShiftStatus.ACTIVE, siteA)));
        guard();
        assertThatThrownBy(() -> service.approvePeriod(T, p.getId(), approver)).isInstanceOf(HandyFlowException.class).hasMessageContaining("still running");
        verify(lines, never()).saveAll(any());
    }

    @Test @DisplayName("a deleted guard is still paid: the lookup includes deleted guards")
    void deletedGuardIsPaid() {
        PayrollPeriod p = period(null);
        Shift s = shift(ShiftStatus.COMPLETED, siteA);
        when(shifts.findByTenantInRange(any(), any(), any())).thenReturn(List.of(s));
        when(lines.findPaidShiftIds(any(), any())).thenReturn(List.of());
        guard();
        service.approvePeriod(T, p.getId(), approver);
        assertThat(saved()).hasSize(1);
        verify(guards, never()).findActiveById(any(), any());
    }

    @Test @DisplayName("the period window is cut at South African midnight")
    void windowIsSast() {
        PayrollPeriod p = period(null);
        when(shifts.findByTenantInRange(any(), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.approvePeriod(T, p.getId(), approver)).isInstanceOf(HandyFlowException.class);
        verify(shifts).findByTenantInRange(T, Instant.parse("2026-10-31T22:00:00Z"), Instant.parse("2026-11-30T22:00:00Z"));
    }

    @Test @DisplayName("downloading an exported period again works and does not change it")
    void repeatExport() {
        PayrollPeriod p = period(null);
        p.approve(approver, java.math.BigDecimal.ONE, 100);
        p.markExported("CSV");
        when(lines.findByPeriod(p.getId())).thenReturn(List.of());
        service.exportCsv(T, p.getId());
        verify(periods, never()).save(any());
    }

    @Test @DisplayName("the first export of an approved period moves it to EXPORTED")
    void firstExport() {
        PayrollPeriod p = period(null);
        p.approve(approver, java.math.BigDecimal.ONE, 100);
        when(lines.findByPeriod(p.getId())).thenReturn(List.of());
        service.exportCsv(T, p.getId());
        assertThat(p.isExported()).isTrue();
        verify(periods).save(p);
    }

    @Test @DisplayName("an export row for a guard whose record is gone is still written")
    void rowsNeverVanish() {
        PayrollPeriod p = period(null);
        p.approve(approver, java.math.BigDecimal.ONE, 100);
        PayrollLineItem li = PayrollLineItem.regular(T, p.getId(), guardId, UUID.randomUUID(), Instant.parse("2026-11-10T04:00:00Z"), Instant.parse("2026-11-10T12:00:00Z"), new java.math.BigDecimal("8.00"), 3000);
        when(lines.findByPeriod(p.getId())).thenReturn(List.of(li));
        when(guards.findAnyById(T, guardId)).thenReturn(Optional.empty());
        String csv = new String(service.exportCsv(T, p.getId()), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv.lines().count()).isEqualTo(2);
        assertThat(csv).contains("(unknown guard)").contains("240.00");
    }
}
