package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.PostureSnapshot;
import za.co.handyflow.platform.legalcompliance.domain.model.SnapshotSource;
import za.co.handyflow.platform.legalcompliance.domain.repository.PostureSnapshotRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryObligationRepository;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Executive GRC overview: a read-only roll-up of the summaries the other services already produce. Nothing here is a new calculation of risk or
 * compliance — every figure is a count taken from a register, collected into a flat map keyed "section.name". Indicators and attention items are derived from
 * that map by {@link ExecutiveRules}, so the same map stored in a snapshot reproduces the same indicators later.
 *
 * Snapshots: one per tenant per day. A manual one can be taken at any time; the monthly job takes one on the 1st. Past figures are therefore the figures as
 * they were, not a recalculation against today's data. FICA is deliberately left out of the overview because it is a restricted area. Ethics shows counts only, never a person's declaration.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveService {

    private final RegulatoryObligationRepository obligations;
    private final CoverageService coverage;
    private final RiskService risks;
    private final ControlTestService testing;
    private final FindingService findings;
    private final AuditService audits;
    private final CapaService capa;
    private final PolicyService policies;
    private final IncidentService incidents;
    private final RegulatoryChangeService changes;
    private final PrivacyService privacy;
    private final ThirdPartyService thirdParties;
    private final LicenceService licences;
    private final CommitteeService committeesSvc;
    private final EthicsService ethics;
    private final PostureSnapshotRepository snapshots;
    private final RegulatoryAuditService trail;

    @Transactional(readOnly = true)
    public Overview overview(TenantId t) {
        Map<String, Long> m = collect(t);
        return new Overview(Instant.now(), m, ExecutiveRules.indicators(m), ExecutiveRules.attention(m));
    }

    /** Every count the overview uses, as one flat map. */
    @Transactional(readOnly = true)
    public Map<String, Long> collect(TenantId t) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("obligations.total", obligationCount(t, null));
        m.put("obligations.compliant", obligationCount(t, ObligationStatus.COMPLIANT));
        m.put("obligations.dueSoon", obligationCount(t, ObligationStatus.DUE_SOON));
        m.put("obligations.overdue", obligationCount(t, ObligationStatus.OVERDUE));
        m.put("obligations.nonCompliant", obligationCount(t, ObligationStatus.NON_COMPLIANT));

        var c = coverage.coverage(t);
        m.put("coverage.obligations", (long) c.obligations());
        m.put("coverage.uncovered", (long) c.uncovered());
        m.put("coverage.coveredUntested", (long) c.coveredUntested());
        m.put("coverage.coveredEffective", (long) c.coveredEffective());
        m.put("coverage.risksWithoutControl", (long) c.risksWithoutControl());

        var r = risks.summary(t);
        m.put("risks.open", r.open()); m.put("risks.critical", r.critical()); m.put("risks.high", r.high()); m.put("risks.aboveAppetite", r.aboveAppetite());
        m.put("risks.outsideTolerance", r.outsideTolerance()); m.put("risks.overdueReview", r.overdueReview()); m.put("risks.accepted", r.accepted()); m.put("risks.unassessed", r.unassessed());

        var ts = testing.summary(t);
        m.put("testing.planned", ts.planned()); m.put("testing.inProgress", ts.inProgress()); m.put("testing.awaitingReview", ts.awaitingReview()); m.put("testing.overdue", ts.overdue());
        m.put("testing.controlsDueForTest", ts.controlsDueForTest()); m.put("testing.controlsNeverTested", ts.controlsNeverTested());

        var f = findings.summary(t);
        m.put("findings.open", f.open()); m.put("findings.overdue", f.overdue()); m.put("findings.critical", f.critical()); m.put("findings.high", f.high());
        m.put("findings.awaitingVerification", f.awaitingVerification());

        var a = audits.summary(t);
        m.put("audits.planned", a.planned()); m.put("audits.inFieldwork", a.inFieldwork()); m.put("audits.reporting", a.reporting()); m.put("audits.overdue", a.overdue());
        m.put("audits.completedThisYear", a.completedThisYear());

        var cp = capa.summary(t);
        m.put("capa.proposed", cp.proposed()); m.put("capa.approved", cp.approved()); m.put("capa.inProgress", cp.inProgress());
        m.put("capa.awaitingVerification", cp.awaitingVerification()); m.put("capa.overdue", cp.overdue());

        var p = policies.summary(t);
        m.put("policies.active", p.active()); m.put("policies.reviewOverdue", p.reviewOverdue()); m.put("policies.reviewDueSoon", p.reviewDueSoon());
        m.put("policies.awaitingApproval", p.awaitingApproval()); m.put("policies.drafts", p.drafts());

        var i = incidents.summary(t);
        m.put("incidents.open", i.open()); m.put("incidents.critical", i.critical()); m.put("incidents.high", i.high());
        m.put("incidents.awaitingAssessment", i.awaitingAssessment()); m.put("incidents.awaitingNotification", i.awaitingNotification());

        var ch = changes.summary(t);
        m.put("changes.pending", ch.pending()); m.put("changes.overdue", ch.overdue()); m.put("changes.dueWithin7Days", ch.dueWithin7Days());

        var pia = privacy.piaSummary(t);
        var paia = privacy.paiaSummary(t);
        var fil = privacy.filingSummary(t);
        m.put("privacy.piaInReview", pia.submitted()); m.put("privacy.piaReviewOverdue", pia.reviewOverdue());
        m.put("privacy.paiaOpen", paia.open()); m.put("privacy.paiaOverdue", paia.overdue());
        m.put("privacy.filingsPending", fil.pending()); m.put("privacy.filingsOverdue", fil.overdue());

        var tp = thirdParties.summary(t);
        m.put("thirdParties.approved", tp.approved()); m.put("thirdParties.suspended", tp.suspended()); m.put("thirdParties.reviewOverdue", tp.reviewOverdue());
        m.put("thirdParties.expiring", tp.expiring()); m.put("thirdParties.expired", tp.expired());

        var l = licences.summary(t);
        m.put("licences.live", l.live()); m.put("licences.valid", l.valid()); m.put("licences.expiring", l.expiring()); m.put("licences.expired", l.expired());
        m.put("licences.renewalInProgress", l.renewalInProgress()); m.put("licences.conditionsOverdue", l.conditionsOverdue()); m.put("licences.conditionsBreached", l.conditionsBreached());

        var g = committeesSvc.summary(t);
        m.put("governance.committees", g.committees()); m.put("governance.meetingsOverdue", g.meetingsOverdue()); m.put("governance.charterReviewOverdue", g.charterReviewOverdue());
        m.put("governance.minutesPending", g.minutesPending()); m.put("governance.actionsOpen", g.actionsOpen()); m.put("governance.actionsOverdue", g.actionsOverdue());
        m.put("governance.withoutQuorumCapacity", g.withoutQuorumCapacity());

        var e = ethics.summary(t);
        m.put("ethics.awaitingReview", e.awaitingReview()); m.put("ethics.inForce", e.inForce()); m.put("ethics.reviewOverdue", e.reviewOverdue()); m.put("ethics.giftsPending", e.giftsPending());
        return m;
    }

    /** Takes today's snapshot. Refused with 409 if one already exists for the day. */
    @Transactional
    public SnapshotPoint takeSnapshot(TenantId t, SnapshotSource source, String by, UUID uid) {
        LocalDate today = LocalDate.now();
        if (snapshots.findByDate(t, today).isPresent())
            throw new HandyFlowException("A snapshot has already been taken today", HttpStatus.CONFLICT, "INVALID_STATE");
        PostureSnapshot s;
        try {
            s = snapshots.saveAndFlush(PostureSnapshot.take(t, today, source, by, collect(t)));
        } catch (DataIntegrityViolationException e) {
            throw new HandyFlowException("A snapshot has already been taken today", HttpStatus.CONFLICT, "INVALID_STATE");
        }
        trail.record(t.getValue(), "POSTURE_SNAPSHOT", s.getId(), "SNAPSHOT_TAKEN", source + " snapshot for " + today, uid, by);
        return point(s);
    }

    /** Snapshots from the last `months` months, oldest first. */
    @Transactional(readOnly = true)
    public List<SnapshotPoint> history(TenantId t, int months) {
        int m = Math.max(1, Math.min(months, 60));
        return snapshots.history(t, LocalDate.now().minusMonths(m)).stream().map(ExecutiveService::point).toList();
    }

    /** Tenants the monthly job covers: those with obligations plus those already snapshotted. There is no tenant registry visible to this module. */
    @Transactional(readOnly = true)
    public Set<TenantId> tenantsForMonthlySnapshot() {
        Set<TenantId> out = new LinkedHashSet<>(snapshots.tenantsWithObligations());
        out.addAll(snapshots.tenantsWithSnapshots());
        return out;
    }

    private long obligationCount(TenantId t, ObligationStatus s) { return obligations.findAllActive(t, s, PageRequest.of(0, 1)).getTotalElements(); }

    private static SnapshotPoint point(PostureSnapshot s) {
        return new SnapshotPoint(s.getSnapshotDate(), s.getSource().name(), s.getTakenByName(), s.getMetrics(), ExecutiveRules.indicators(s.getMetrics()));
    }
}
