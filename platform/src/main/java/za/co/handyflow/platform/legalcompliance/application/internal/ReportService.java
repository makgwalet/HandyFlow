package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.repository.PostureSnapshotRepository;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.Indicator;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportInfo;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

import static za.co.handyflow.platform.legalcompliance.application.internal.ReportRules.*;

/**
 * The standard reports. Each is a table built from what the registers already expose, so a report can never disagree with its screen. Rules that keep them safe:
 *  - FICA and conflict-of-interest declarations are not reports here at all (restricted areas);
 *  - personal data of the public (PAIA requesters, data subjects, declarants) is never a column; incident descriptions and free-text notes are left out too;
 *  - only the executive scorecard can be run "as of" an earlier date, and then it is computed from the stored snapshot, not from today's data;
 *  - downloading is a separate act (exportCsv) which is written to the regulatory audit trail with the report, the row count and who did it.
 * Output is capped at ReportRules.MAX_ROWS rows and says so when cut.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final String ENTITY = "REPORT";

    private final ExecutiveService executive;
    private final RegulatoryObligationService obligations;
    private final RiskService risks;
    private final FindingService findings;
    private final CapaService capa;
    private final PolicyService policies;
    private final IncidentService incidents;
    private final PrivacyService privacy;
    private final ThirdPartyService thirdParties;
    private final LicenceService licences;
    private final CommitteeService committees;
    private final ContinuityService continuity;
    private final EsgService esg;
    private final PostureSnapshotRepository snapshots;
    private final RegulatoryAuditService trail;

    public record ExportFile(String fileName, byte[] bytes) {}

    private record Ctx(TenantId tenant, LocalDate today, LocalDate asOf) {}

    private record Def(ReportInfo info, List<String> columns, Function<Ctx, List<List<String>>> rows) {}

    private final Map<String, Def> defs = build();

    public List<ReportInfo> catalogue() { return defs.values().stream().map(Def::info).toList(); }

    @Transactional(readOnly = true)
    public ReportResult run(TenantId t, String key, String asOf, String by) {
        Def d = defs.get(key == null ? "" : key.toUpperCase());
        if (d == null) throw new ResourceNotFoundException("Report", String.valueOf(key));
        LocalDate asOfDate = null;
        if (asOf != null && !asOf.isBlank()) {
            if (!d.info().supportsAsOf()) throw new HandyFlowException("This report can only be run for today", HttpStatus.BAD_REQUEST, "VALIDATION");
            try { asOfDate = LocalDate.parse(asOf.trim()); } catch (RuntimeException e) { throw new HandyFlowException("The date must look like 2026-03-31", HttpStatus.BAD_REQUEST, "VALIDATION"); }
            if (asOfDate.isAfter(today())) throw new HandyFlowException("The date cannot be in the future", HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        List<List<String>> rows = d.rows().apply(new Ctx(t, today(), asOfDate));
        boolean truncated = rows.size() > MAX_ROWS;
        List<List<String>> out = truncated ? rows.subList(0, MAX_ROWS) : rows;
        return new ReportResult(d.info().key(), d.info().title(), d.info().category(), Instant.now(), by, asOfDate == null ? null : asOfDate.toString(), d.columns(), out, out.size(), truncated);
    }

    /** Runs the report and records the export. The audit entry is written in the same transaction as the run, so there is no export without a record. */
    @Transactional
    public ExportFile exportCsv(TenantId t, String key, String asOf, UUID uid, String uname) {
        ReportResult r = run(t, key, asOf, uname);
        trail.record(t.getValue(), ENTITY, UUID.nameUUIDFromBytes(("report:" + r.key()).getBytes(StandardCharsets.UTF_8)), "EXPORTED",
                r.title() + ": " + r.rowCount() + " row(s)" + (r.truncated() ? " (cut at " + MAX_ROWS + ")" : "") + (r.asOf() == null ? "" : ", as of " + r.asOf()) + ", CSV", uid, uname);
        byte[] body = ("﻿" + csv(r.columns(), r.rows())).getBytes(StandardCharsets.UTF_8);
        return new ExportFile(fileName(r.key(), today()), body);
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    // ── The catalogue ──

    private Map<String, Def> build() {
        Map<String, Def> m = new LinkedHashMap<>();
        add(m, "EXEC_SCORECARD", "Management", "Executive scorecard", "Each indicator with its counts and percentage; can be run as of a past snapshot date. No composite score.", false, true,
                List.of("Indicator", "Count", "Out of", "Percent", "How it is worked out"), c -> {
                    List<Indicator> ind = c.asOf() == null ? executive.overview(c.tenant()).indicators()
                            : ExecutiveRules.indicators(snapshots.findByDate(c.tenant(), c.asOf()).orElseThrow(() -> new ResourceNotFoundException("Snapshot", c.asOf().toString())).getMetrics());
                    return ind.stream().map(i -> List.of(i.label(), num(i.numerator()), num(i.denominator()), i.percent() == null ? "n/a" : i.percent() + "%", i.formula())).toList();
                });
        add(m, "ATTENTION", "Management", "Items needing attention", "Everything currently overdue or flagged across the registers, critical first.", false, false,
                List.of("Severity", "Item", "Count", "Where"), c -> executive.overview(c.tenant()).attention().stream().map(a -> List.of(a.severity(), a.text(), num(a.count()), a.tab())).toList());
        add(m, "OBLIGATIONS", "Regulatory", "Obligation register", "Every obligation with its owner, review date and status.", true, false,
                List.of("Title", "Category", "Regulation", "Owner", "Review date", "Recurrence", "Status"), c -> obligations.listAll(c.tenant()).stream()
                        .map(o -> List.of(text(o.getTitle()), text(o.getCategory()), text(o.getRegulationReference()), text(o.getResponsibleUserName()), date(o.getReviewDate()), text(o.getRecurrence()), text(o.getStatus()))).toList());
        add(m, "RISKS", "Risk", "Risk register", "Open risks with inherent and residual scores, treatment and review dates.", true, false,
                List.of("Reference", "Title", "Category", "Status", "Owner", "Inherent score", "Residual score", "Residual rating", "Position", "Treatment", "Next review", "Review overdue", "Controls", "Obligations"),
                c -> risks.list(c.tenant(), null, null, false).stream().map(r -> {
                    var a = r.assessment();
                    return List.of(text(r.reference()), text(r.title()), text(r.category()), text(r.status()), text(r.ownerName()), a == null ? "" : num(a.inherentScore()), a == null ? "" : num(a.residualScore()),
                            a == null ? "" : text(a.residualRating()), a == null ? "" : text(a.residualPosition()), text(r.treatmentStrategy()), date(r.nextReviewDate()), yesNo(r.reviewOverdue()), num(r.controlCount()), num(r.obligationCount()));
                }).toList());
        add(m, "FINDINGS", "Controls", "Findings register", "Open findings by severity, owner and due date.", true, false,
                List.of("Reference", "Title", "Severity", "Status", "Owner", "Due", "Overdue", "Days overdue", "Control", "Reopened"),
                c -> findings.list(c.tenant(), null, null, false).stream().map(f -> List.of(text(f.reference()), text(f.title()), text(f.severity()), text(f.status()), text(f.ownerName()), date(f.dueDate()),
                        yesNo(f.overdue()), num(daysOverdue(f.dueDate(), c.today())), text(f.controlLabel()), num(f.reopenedCount()))).toList());
        add(m, "CAPA_AGEING", "CAPA", "Corrective action ageing", "Open corrective actions with ownership and how long they have been overdue.", true, false,
                List.of("Reference", "Title", "Type", "Status", "Owner", "Due", "Overdue", "Days overdue", "Linked to", "Returned", "Reopened"),
                c -> capa.list(c.tenant(), null, false, null, null).stream().map(x -> List.of(text(x.reference()), text(x.title()), text(x.capaType()), text(x.status()), text(x.ownerName()), date(x.dueDate()),
                        yesNo(x.overdue()), num(daysOverdue(x.dueDate(), c.today())), text(x.parentLabel()), num(x.returnedCount()), num(x.reopenedCount()))).toList());
        add(m, "POLICIES", "Governance", "Policy review status", "Policies with their published version, effective date and next review.", true, false,
                List.of("Reference", "Title", "Type", "Status", "Owner", "Published version", "Effective", "Next review", "Review overdue", "Review due soon"),
                c -> policies.list(c.tenant(), false).stream().map(p -> List.of(text(p.reference()), text(p.title()), text(p.policyType()), text(p.status()), text(p.ownerName()), text(p.publishedVersion()),
                        date(p.effectiveDate()), date(p.nextReviewDue()), yesNo(p.reviewOverdue()), yesNo(p.reviewDueSoon()))).toList());
        add(m, "COMMITTEE_ACTIONS", "Governance", "Committee actions", "Every action given at a committee meeting, with owner and due date.", true, false,
                List.of("Committee", "Meeting", "Action", "Owner", "Due", "Status", "Overdue", "Completed"),
                c -> committees.actions(c.tenant(), false).stream().map(a -> List.of(text(a.committeeName()), date(a.meetingDate()), text(a.description()), text(a.ownerName()), date(a.dueDate()), text(a.status()),
                        yesNo(a.overdue()), date(a.completedOn()))).toList());
        add(m, "INCIDENTS", "Privacy", "Incident register", "Incidents with severity, status and notification position. Descriptions, names and affected people are not included.", false, false,
                List.of("Reference", "Type", "Severity", "Status", "Discovered", "Personal information involved", "People affected (count)", "Notification required", "Regulator notified", "Assessment overdue"),
                c -> incidents.list(c.tenant(), null, true).stream().map(i -> List.of(text(i.reference()), text(i.incidentType()), text(i.severity()), text(i.status()), text(i.discoveredAt()),
                        yesNo(i.personalInfoInvolved()), num(i.dataSubjectsAffected()), yesNo(i.notificationRequired()), text(i.regulatorNotifiedAt()), yesNo(i.assessmentOverdue()))).toList());
        add(m, "PAIA", "Privacy", "PAIA request timeliness", "Access requests with due dates and decisions. The requester's details and the records asked for are not included.", false, false,
                List.of("Reference", "Received", "Due", "Effective due", "Status", "Decision", "Decision date", "Overdue", "Extended"),
                c -> privacy.listPaia(c.tenant(), null, true).stream().map(p -> List.of(text(p.reference()), date(p.receivedDate()), date(p.dueDate()), date(p.effectiveDue()), text(p.status()), text(p.decision()),
                        date(p.decisionDate()), yesNo(p.overdue()), yesNo(p.extendedUntil() != null))).toList());
        add(m, "FILINGS", "Privacy", "Regulator filings", "Registrations, annual reports and assessments with due dates and proof status.", true, false,
                List.of("Reference", "Type", "Title", "Period", "Due", "Status", "Submitted", "Regulator reference", "Proof files", "Overdue"),
                c -> privacy.listFilings(c.tenant(), true).stream().map(f -> List.of(text(f.reference()), text(f.filingType()), text(f.title()), text(f.periodLabel()), date(f.dueDate()), text(f.status()),
                        date(f.submittedOn()), text(f.regulatorReference()), num(f.proof() == null ? 0 : f.proof().size()), yesNo(f.overdue()))).toList());
        add(m, "THIRD_PARTIES", "Third-party", "Third-party status", "Suppliers and partners with criticality, risk, review dates and document expiry counts.", true, false,
                List.of("Reference", "Name", "Type", "Criticality", "Risk", "Status", "Owner", "Handles personal information", "Next review", "Review overdue", "Documents expiring", "Documents expired"),
                c -> thirdParties.list(c.tenant(), null).stream().map(p -> List.of(text(p.reference()), text(p.name()), text(p.partyType()), text(p.criticality()), text(p.riskRating()), text(p.status()), text(p.ownerName()),
                        yesNo(p.handlesPersonalInfo()), date(p.nextReviewDue()), yesNo(p.reviewOverdue()), num(p.expiringCount()), num(p.expiredCount()))).toList());
        add(m, "LICENCES", "Regulatory", "Licences and permits", "Licences with expiry, days left, conditions overdue and breached.", true, false,
                List.of("Reference", "Type", "Title", "Authority", "Number", "Status", "Expires", "Days to expiry", "Owner", "Conditions", "Conditions overdue", "Conditions breached"),
                c -> licences.list(c.tenant(), null, false).stream().map(l -> List.of(text(l.reference()), text(l.licenceType()), text(l.title()), text(l.issuingAuthority()), text(l.licenceNumber()), text(l.displayStatus()),
                        date(l.expiryDate()), num(l.daysToExpiry()), text(l.ownerName()), num(l.conditionCount()), num(l.conditionsOverdue()), num(l.conditionsBreached()))).toList());
        add(m, "CONTINUITY", "Resilience", "Business continuity", "Critical processes with recovery objectives, approved plan, last exercise and what is overdue.", true, false,
                List.of("Reference", "Process", "Criticality", "Owner", "RTO (h)", "RPO (h)", "Approved plan", "Plan review due", "Last exercise", "Last outcome", "Exercise due", "Exercise overdue", "Impact analysis overdue"),
                c -> continuity.listProcesses(c.tenant(), false).stream().map(p -> List.of(text(p.reference()), text(p.name()), text(p.criticality()), text(p.ownerName()), num(p.rtoHours()), num(p.rpoHours()),
                        text(p.approvedPlanReference() == null ? "None" : p.approvedPlanReference() + " " + p.approvedPlanVersion()), date(p.planReviewDue()), date(p.lastTestDate()), text(p.lastTestOutcome()),
                        date(p.testDueOn()), yesNo(p.testOverdue()), yesNo(p.biaOverdue()))).toList());
        add(m, "ESG", "Resilience", "ESG metrics", "ESG metrics with latest reading, target, whether it was met and whether the next reading is overdue.", true, false,
                List.of("Reference", "Metric", "Category", "Unit", "Frequency", "Owner", "Framework", "Target", "Latest period end", "Latest value", "Target status", "Reading overdue"),
                c -> esg.list(c.tenant(), false).stream().map(x -> List.of(text(x.reference()), text(x.name()), text(x.category()), text(x.unit()), text(x.frequency()), text(x.ownerName()), text(x.frameworkRef()),
                        text(x.targetValue() == null ? null : x.targetValue().stripTrailingZeros().toPlainString()), date(x.latestPeriodEnd()),
                        text(x.latestValue() == null ? null : x.latestValue().stripTrailingZeros().toPlainString()), text(x.latestTargetStatus()), yesNo(x.readingOverdue()))).toList());
        return Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, Def> m, String key, String category, String title, String description, boolean names, boolean asOf, List<String> columns, Function<Ctx, List<List<String>>> rows) {
        m.put(key, new Def(new ReportInfo(key, category, title, description, names, asOf), columns, rows));
    }
}
