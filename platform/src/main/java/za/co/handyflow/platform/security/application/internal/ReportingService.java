// security/application/internal/ReportingService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.*;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ReportingService — aggregates the raw data for the three Phase 3.5 reports.
 *
 * Three reports:
 *
 * 1. SITE COVERAGE — for a specific site and month:
 *    Total shifts scheduled vs completed vs missed, guard-hours logged,
 *    checkpoint scan counts, incidents by severity. The client-facing
 *    proof-of-SLA artifact: "we provided X hours of coverage, completed Y
 *    patrols, and responded to Z incidents."
 *
 * 2. GUARD ATTENDANCE — for a specific guard and month:
 *    Shifts attended/missed/late, total hours worked, incidents logged,
 *    checkpoint scans completed. Feeds the HR/payroll export and the
 *    supervisor performance review.
 *
 * 3. MONTHLY SUMMARY — tenant-wide rollup for a month:
 *    Per-site coverage, overall shift completion rate, incident heat map
 *    by severity, guard utilisation. The executive view.
 *
 * WHY aggregation in the service rather than a reporting DB view?
 * Reporting views work well for fixed schemas, but the security module's
 * schema is still actively evolving (Phase 3 added several tables). Service-
 * layer aggregation is easier to change and easier to test. If query
 * performance becomes an issue as data grows, the natural next step is a
 * materialised view or a dedicated reporting replica — not a rewrite, just
 * a change to where the data comes from.
 *
 * FIX: backlog 7.1/7.2. Shift.java's own class Javadoc, written when
 * PULLED was introduced, explicitly flagged that this service's
 * completion-rate math treated anything not COMPLETED/CANCELLED as
 * effectively missed — wrong for a pull, and confirmed worse in practice
 * than that comment anticipated: PULLED shifts were silently excluded
 * from every count AND every hours total across all three reports, not
 * merely miscounted. Per your confirmed decision (a pulled shift counts
 * its partial hours worked toward coverage/guard-hours credit), fixed
 * via one centralized hoursWorked() helper below rather than patching
 * each of the ~15 individual filter/sum call sites separately — this
 * also directly satisfies 7.2 (a real enum switch, not string equality,
 * is exactly what would have forced a compile-time decision when PULLED
 * was originally added, which is the whole point of that finding).
 * <p>
 * PULLED is a genuine fourth bucket in every report/breakdown below, not
 * folded into completedShifts or missedShifts — a supervisor pull isn't
 * a lesser completion or a disguised miss, it's its own outcome. Each
 * report's own pre-existing "how many shifts count toward the rate
 * denominator" convention is extended to include pulled consistently,
 * not homogenized across reports — Site Coverage already included
 * `scheduled` in its denominator, Guard Attendance didn't, Monthly
 * Summary's denominator was always literally every shift; each keeps
 * its own shape, just correctly including pulled now everywhere
 * completed/missed/cancelled already were.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final ShiftRepository           shiftRepository;
    private final IncidentRepository        incidentRepository;
    private final CheckpointLogRepository   checkpointLogRepository;
    private final SiteRepository            siteRepository;
    private final GuardRepository           guardRepository;
    private final PatrolRoundRepository     patrolRoundRepository;

    /**
     * FIX: backlog 7.1/7.2 — the single place hours-credit is decided per
     * shift status, replacing the old "only COMPLETED counts" filter
     * that silently zeroed out PULLED everywhere it appeared.
     * <p>
     * PULLED uses startAt (the shift's scheduled start) through pulledAt
     * (the real, recorded moment the supervisor pulled the guard) as the
     * worked-hours window. FLAGGED ASSUMPTION, not verified fact: Shift
     * has no separate actualStartAt/clockedInAt field distinct from the
     * scheduled startAt, so this assumes the guard began working at the
     * scheduled start time. If a more precise "actually clocked in"
     * timestamp exists elsewhere (e.g. DeviceSessionService) and should
     * be used instead, that's a real follow-up — not guessed at here.
     */
    private double hoursWorked(Shift s) {
        return switch (s.getStatus()) {
            case COMPLETED -> Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() / 60.0;
            case PULLED -> s.getPulledAt() != null
                    ? Math.max(0, Duration.between(s.getStartAt(), s.getPulledAt()).toMinutes()) / 60.0
                    : 0.0;
            case SCHEDULED, ACTIVE, MISSED, CANCELLED -> 0.0;
        };
    }

    // ── 1. Site Coverage Report ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SiteCoverageReport getSiteCoverageReport(TenantId tenantId, UUID siteId,
                                                    YearMonth month) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId.toString()));

        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to   = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Shift> shifts = shiftRepository.findBySiteInRange(tenantId, siteId, from, to);
        List<Incident> incidents = incidentRepository.findBySiteInRange(tenantId, siteId, from, to);
        long scanCount = checkpointLogRepository.countBySiteInRange(tenantId, siteId, from, to);

        // Shift breakdowns
        long scheduled  = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.SCHEDULED).count();
        long active     = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.ACTIVE).count();
        long completed  = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.COMPLETED).count();
        long missed     = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.MISSED).count();
        long cancelled  = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.CANCELLED).count();
        long pulled     = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.PULLED).count();

        // Total guard-hours — now includes PULLED shifts' partial hours
        // worked, via hoursWorked() above.
        double totalHours = shifts.stream()
                .mapToDouble(this::hoursWorked)
                .sum();

        // Patrol rounds summary
        List<PatrolRound> rounds = shifts.stream()
                .flatMap(s -> patrolRoundRepository.findByShift(s.getId()).stream())
                .toList();
        long roundsExpected = rounds.size();
        long roundsCompleted = rounds.stream()
                .filter(r -> r.getStatus().name().equals("COMPLETE")).count();
        long roundsMissed = rounds.stream()
                .filter(r -> r.getStatus().name().equals("MISSED")).count();

        // Incident breakdown by severity
        Map<String, Long> incidentsBySeverity = incidents.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSeverity() != null ? i.getSeverity() : "UNKNOWN",
                        Collectors.counting()));

        // Completion rate % — denominator extended to include pulled
        // (a real scheduleable outcome, same as completed/missed),
        // keeping the exact same "totalScheduleable" shape this report
        // already used. pulled is deliberately NOT added to the
        // numerator — a pull is a genuinely distinct outcome from a
        // normal completion, not a lesser version of one, even though
        // it now correctly contributes hours above.
        long totalScheduleable = scheduled + completed + missed + pulled;
        double completionRate = totalScheduleable > 0
                ? (completed * 100.0 / totalScheduleable) : 0.0;

        return new SiteCoverageReport(
                siteId, site.getName(), month.toString(),
                (int) (scheduled + active + completed + missed + cancelled + pulled),
                (int) completed, (int) missed, (int) cancelled, (int) pulled,
                Math.round(totalHours * 10) / 10.0,
                Math.round(completionRate * 10) / 10.0,
                (int) roundsExpected, (int) roundsCompleted, (int) roundsMissed,
                (int) scanCount, incidents.size(),
                incidentsBySeverity);
    }

    // ── 2. Guard Attendance Report ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GuardAttendanceReport getGuardAttendanceReport(TenantId tenantId, UUID guardId,
                                                          YearMonth month) {
        Guard guard = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));

        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to   = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Shift> shifts    = shiftRepository.findByGuardInRange(tenantId, guardId, from, to);
        List<Incident> incidents = incidentRepository.findByGuardInRange(tenantId, guardId, from, to);
        long scanCount = checkpointLogRepository.countByGuardInRange(tenantId, guardId, from, to);

        long completed = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.COMPLETED).count();
        long missed    = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.MISSED).count();
        long cancelled = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.CANCELLED).count();
        long pulled    = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.PULLED).count();

        double totalHours = shifts.stream()
                .mapToDouble(this::hoursWorked)
                .sum();

        // Per-site breakdown
        Map<UUID, List<Shift>> bySite = shifts.stream()
                .collect(Collectors.groupingBy(Shift::getSiteId));
        List<GuardAttendanceReport.SiteAttendance> siteBreakdown = bySite.entrySet().stream()
                .map(entry -> {
                    String siteName = siteRepository.findActiveById(tenantId, entry.getKey())
                            .map(Site::getName).orElse("Unknown site");
                    long sCompleted = entry.getValue().stream()
                            .filter(s -> s.getStatus() == ShiftStatus.COMPLETED).count();
                    long sMissed = entry.getValue().stream()
                            .filter(s -> s.getStatus() == ShiftStatus.MISSED).count();
                    long sPulled = entry.getValue().stream()
                            .filter(s -> s.getStatus() == ShiftStatus.PULLED).count();
                    double sHours = entry.getValue().stream()
                            .mapToDouble(this::hoursWorked)
                            .sum();
                    return new GuardAttendanceReport.SiteAttendance(
                            entry.getKey(), siteName, entry.getValue().size(),
                            (int) sCompleted, (int) sMissed, (int) sPulled,
                            Math.round(sHours * 10) / 10.0);
                })
                .sorted(Comparator.comparing(GuardAttendanceReport.SiteAttendance::siteName))
                .toList();

        // Denominator extended to include pulled — same shape this
        // report already used (completed + missed + cancelled), just
        // correctly including pulled alongside them now.
        long total = completed + missed + cancelled + pulled;
        double attendanceRate = total > 0 ? (completed * 100.0 / total) : 0.0;

        return new GuardAttendanceReport(
                guardId, guard.getFullName(), month.toString(),
                shifts.size(), (int) completed, (int) missed, (int) cancelled, (int) pulled,
                Math.round(totalHours * 10) / 10.0,
                Math.round(attendanceRate * 10) / 10.0,
                (int) scanCount, incidents.size(), siteBreakdown);
    }

    // ── 3. Monthly Summary Report ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MonthlySummaryReport getMonthlySummaryReport(TenantId tenantId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to   = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Shift>    allShifts    = shiftRepository.findByTenantInRange(tenantId, from, to);
        List<Incident> allIncidents = incidentRepository.findByTenantInRange(tenantId, from, to);

        long totalShifts     = allShifts.size();
        long completedShifts = allShifts.stream().filter(s -> s.getStatus() == ShiftStatus.COMPLETED).count();
        long missedShifts    = allShifts.stream().filter(s -> s.getStatus() == ShiftStatus.MISSED).count();
        long pulledShifts    = allShifts.stream().filter(s -> s.getStatus() == ShiftStatus.PULLED).count();

        double totalHours = allShifts.stream()
                .mapToDouble(this::hoursWorked)
                .sum();

        // NOTE: this denominator was always literally every shift
        // (totalShifts = allShifts.size(), unfiltered) — PULLED shifts
        // were already correctly included in the rate's denominator here
        // even before this fix; only the hours total and the missing
        // pulledShifts count were the actual gaps at this report level.
        double overallCompletionRate = totalShifts > 0
                ? (completedShifts * 100.0 / totalShifts) : 0.0;

        // Per-site rollup
        Map<UUID, List<Shift>> shiftsBySite = allShifts.stream()
                .collect(Collectors.groupingBy(Shift::getSiteId));
        Map<UUID, List<Incident>> incidentsBySite = allIncidents.stream()
                .filter(i -> i.getSiteId() != null)
                .collect(Collectors.groupingBy(Incident::getSiteId));

        List<MonthlySummaryReport.SiteSummary> siteSummaries = shiftsBySite.entrySet().stream()
                .map(entry -> {
                    UUID siteId = entry.getKey();
                    String siteName = siteRepository.findActiveById(tenantId, siteId)
                            .map(Site::getName).orElse("Unknown site");
                    long sCompleted = entry.getValue().stream()
                            .filter(s -> s.getStatus() == ShiftStatus.COMPLETED).count();
                    long sMissed = entry.getValue().stream()
                            .filter(s -> s.getStatus() == ShiftStatus.MISSED).count();
                    long sPulled = entry.getValue().stream()
                            .filter(s -> s.getStatus() == ShiftStatus.PULLED).count();
                    double sHours = entry.getValue().stream()
                            .mapToDouble(this::hoursWorked)
                            .sum();
                    int sIncidents = incidentsBySite.getOrDefault(siteId, List.of()).size();
                    // Same note as overallCompletionRate above — this
                    // denominator (entry.getValue().size()) was always
                    // every shift at this site, pulled included.
                    double sCoverageRate = entry.getValue().size() > 0
                            ? (sCompleted * 100.0 / entry.getValue().size()) : 0.0;

                    return new MonthlySummaryReport.SiteSummary(
                            siteId, siteName, entry.getValue().size(),
                            (int) sCompleted, (int) sMissed, (int) sPulled,
                            Math.round(sHours * 10) / 10.0,
                            Math.round(sCoverageRate * 10) / 10.0,
                            sIncidents);
                })
                .sorted(Comparator.comparing(MonthlySummaryReport.SiteSummary::siteName))
                .toList();

        // Incident heat map
        Map<String, Long> incidentsBySeverity = allIncidents.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSeverity() != null ? i.getSeverity() : "UNKNOWN",
                        Collectors.counting()));

        // Unique guards who worked this month
        long activeGuards = allShifts.stream()
                .map(Shift::getGuardId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return new MonthlySummaryReport(
                tenantId.getValue(), month.toString(),
                (int) totalShifts, (int) completedShifts, (int) missedShifts, (int) pulledShifts,
                Math.round(totalHours * 10) / 10.0,
                Math.round(overallCompletionRate * 10) / 10.0,
                allIncidents.size(), incidentsBySeverity,
                (int) activeGuards, siteSummaries);
    }
}