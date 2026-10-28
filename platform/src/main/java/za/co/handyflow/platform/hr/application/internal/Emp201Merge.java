package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.hr.domain.model.HrEmp201Run;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.PayRunWorkflowRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmp201Repository;
import za.co.handyflow.platform.hr.domain.repository.HrEmp201RunRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayslipRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Keeps one EMP201 per tax month. A pay run joins the month's return while that return is still a draft; once a return has been filed
 * with SARS it is never changed, and a run finalised afterwards gets a return of its own for the same month (an additional declaration).
 * The return's totals are always the sum of the runs it covers, so a recalculated or cancelled run is simply taken out and the totals worked
 * again; a return with no runs left is removed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Emp201Merge {

    private final HrEmp201Repository returns;
    private final HrEmp201RunRepository links;
    private final HrPayRunRepository runs;
    private final HrPayslipRepository payslips;

    /** Refuses when the run is already part of a return that has been filed. */
    public void requireNotFiled(TenantId tenantId, UUID runId) {
        if (returns.forPayRun(tenantId, runId).stream().anyMatch(e -> !"DRAFT".equals(e.getStatus())))
            throw new IllegalStateException("The EMP201 of this run has already been submitted, so the run cannot be recalculated or cancelled.");
    }

    /** Takes the run out of its return (if any) and works the return's totals again. */
    public void detach(TenantId tenantId, UUID runId) {
        requireNotFiled(tenantId, runId);
        for (HrEmp201 e : returns.forPayRun(tenantId, runId)) {
            links.deleteById(runId);
            links.flush();
            recompute(e);
        }
    }

    /** Puts a calculated run into its month's return, making one when there is none still open. */
    public HrEmp201 attach(TenantId tenantId, HrPayRun run) {
        detach(tenantId, run.getId());
        LocalDate month = run.getPeriodEnd().withDayOfMonth(1);
        List<HrEmp201> open = returns.draftForMonth(tenantId, month);
        HrEmp201 target = open.isEmpty() ? null : open.get(0);
        if (target == null) {
            target = HrEmp201.create(tenantId, run.getId(), run.getPeriodStart(), run.getPeriodEnd(),
                    nz(run.getTotalPaye()), nz(run.getTotalUif()), nz(run.getTotalSdl()));
            target = returns.save(target);
        }
        links.save(HrEmp201Run.of(target.getId(), run.getId()));
        links.flush();
        recompute(target);
        log.info("Pay run {} is part of the EMP201 for {}", run.getPayRunNumber(), month);
        return target;
    }

    /** The runs a return covers. A return from before runs were linked is read through its own run. */
    public List<HrPayRun> runsOf(TenantId tenantId, HrEmp201 e) {
        List<UUID> ids = links.forEmp201(e.getId()).stream().map(HrEmp201Run::getPayRunId).toList();
        if (ids.isEmpty() && e.getPayRunId() != null) ids = List.of(e.getPayRunId());
        return ids.isEmpty() ? List.of() : runs.findAllById(ids).stream().filter(r -> tenantId.getValue().equals(r.getTenantId())).toList();
    }

    /** True when every run of the return that has not been cancelled is issued. A return with no runs counts as issued. */
    public boolean allIssued(TenantId tenantId, HrEmp201 e) {
        return runsOf(tenantId, e).stream().filter(r -> !"CANCELLED".equals(r.getStatus())).allMatch(r -> PayRunWorkflowRules.isIssued(r.getStatus()));
    }

    /** For a list of returns at once: which have every run issued. */
    public java.util.Set<UUID> issuedReturns(List<HrEmp201> all) {
        java.util.Map<UUID, List<UUID>> byReturn = new java.util.HashMap<>();
        for (HrEmp201Run l : all.isEmpty() ? List.<HrEmp201Run>of() : links.forEmp201s(all.stream().map(HrEmp201::getId).toList()))
            byReturn.computeIfAbsent(l.getEmp201Id(), k -> new java.util.ArrayList<>()).add(l.getPayRunId());
        for (HrEmp201 e : all) if (!byReturn.containsKey(e.getId()) && e.getPayRunId() != null) byReturn.put(e.getId(), List.of(e.getPayRunId()));
        java.util.Set<UUID> allIds = new java.util.HashSet<>();
        byReturn.values().forEach(allIds::addAll);
        java.util.Map<UUID, HrPayRun> byId = new java.util.HashMap<>();
        if (!allIds.isEmpty()) runs.findAllById(allIds).forEach(r -> byId.put(r.getId(), r));
        java.util.Set<UUID> out = new java.util.HashSet<>();
        for (HrEmp201 e : all) {
            List<UUID> rs = byReturn.getOrDefault(e.getId(), List.of());
            if (rs.stream().map(byId::get).filter(java.util.Objects::nonNull).filter(r -> !"CANCELLED".equals(r.getStatus()))
                    .allMatch(r -> PayRunWorkflowRules.isIssued(r.getStatus()))) out.add(e.getId());
        }
        return out;
    }

    /** The return's totals are the sum of its runs that have not been cancelled; none left means the return goes. */
    void recompute(HrEmp201 e) {
        List<UUID> ids = links.forEmp201(e.getId()).stream().map(HrEmp201Run::getPayRunId).toList();
        List<HrPayRun> live = ids.isEmpty() ? List.of() : runs.findAllById(ids).stream().filter(r -> !"CANCELLED".equals(r.getStatus())).toList();
        if (live.isEmpty()) {
            returns.delete(e);
            return;
        }
        BigDecimal paye = sum(live, HrPayRun::getTotalPaye), uif = sum(live, HrPayRun::getTotalUif), sdl = sum(live, HrPayRun::getTotalSdl);
        BigDecimal eti = payslips.sumEti(live.stream().map(HrPayRun::getId).toList());
        HrPayRun first = live.stream().min(Comparator.comparing(HrPayRun::getPeriodStart).thenComparing(HrPayRun::getPayRunNumber)).orElseThrow();
        LocalDate start = live.stream().map(HrPayRun::getPeriodStart).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = live.stream().map(HrPayRun::getPeriodEnd).max(LocalDate::compareTo).orElseThrow();
        e.refresh(first.getId(), start, end, paye, uif, sdl, eti == null ? BigDecimal.ZERO : eti);
        returns.save(e);
    }

    private static BigDecimal sum(List<HrPayRun> rs, java.util.function.Function<HrPayRun, BigDecimal> f) {
        return rs.stream().map(r -> nz(f.apply(r))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
