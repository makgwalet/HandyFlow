package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.ImpactStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.PostureSnapshotRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryEventRepository;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryChangeDtos.ImpactResponse;
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
 *  - each report has a minimum audience: PROVIDER (a consultant reading a client's workspace) < STAFF < ADMIN. The Audit Trail is ADMIN only and Litigation is not shown to providers;
 *    callers that do not say who is asking get PROVIDER, the most restrictive view;
 *  - downloading is a separate act (exportCsv) which is written to the regulatory audit trail with the report, the row count and who did it.
 * Output is capped at ReportRules.MAX_ROWS rows and says so when cut.
 */
@Service("legalComplianceReportService")
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
    private final ControlService controls;
    private final LitigationMatterService litigation;
    private final RegulatoryChangeService changes;
    private final PopiaProcessingActivityService popia;
    private final DsarRequestService dsar;
    private final RegulatoryEventRepository events;
    private final PostureSnapshotRepository snapshots;
    private final RegulatoryAuditService trail;

    public record ExportFile(String fileName, byte[] bytes, String contentType) {}

    public enum Format {
        CSV("csv", "text/csv; charset=utf-8"), XLSX("xlsx", ReportFiles.XLSX_TYPE), PDF("pdf", ReportFiles.PDF_TYPE);
        public final String ext, contentType;
        Format(String ext, String contentType) { this.ext = ext; this.contentType = contentType; }
        /** Null or blank means CSV; anything unrecognised is a 400, not a silent fallback. */
        public static Format parse(String s) {
            if (s == null || s.isBlank()) return CSV;
            try { return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new HandyFlowException("Unknown export format: use csv, xlsx or pdf", org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION"); }
        }
    }

    private record Ctx(TenantId tenant, LocalDate today, LocalDate asOf) {}

    /** Who is asking. Each report names the lowest audience that may see it. */
    public enum Audience { PROVIDER, STAFF, ADMIN }

    private record Def(ReportInfo info, List<String> columns, Function<Ctx, List<List<String>>> rows, Audience min) {}

    private final Map<String, Def> defs = build();

    /** The most restrictive view (a provider's). Callers that mean staff or admin must say so. */
    public List<ReportInfo> catalogue() { return catalogue(Audience.PROVIDER); }

    public List<ReportInfo> catalogue(Audience who) { return defs.values().stream().filter(d -> allowed(d, who)).map(Def::info).toList(); }

    /** The most restrictive view (a provider's). */
    @Transactional(readOnly = true)
    public ReportResult run(TenantId t, String key, String asOf, String by) { return run(t, key, asOf, by, Audience.PROVIDER); }

    private static boolean allowed(Def d, Audience who) { return visible(d.min(), who); }

    /** True when someone of audience `who` may see a report whose lowest audience is `min`. A missing audience sees nothing. */
    static boolean visible(Audience min, Audience who) { return who != null && who.ordinal() >= min.ordinal(); }

    @Transactional(readOnly = true)
    public ReportResult run(TenantId t, String key, String asOf, String by, Audience who) {
        Def d = defs.get(key == null ? "" : key.toUpperCase());
        // A report the caller may not see is reported exactly like one that does not exist.
        if (d == null || !allowed(d, who)) throw new ResourceNotFoundException("Report", String.valueOf(key));
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
    public ExportFile exportCsv(TenantId t, String key, String asOf, UUID uid, String uname, Audience who) { return export(t, key, asOf, uid, uname, who, Format.CSV); }

    @Transactional
    public ExportFile export(TenantId t, String key, String asOf, UUID uid, String uname, Audience who, Format format) {
        ReportResult r = run(t, key, asOf, uname, who);
        trail.record(t.getValue(), ENTITY, UUID.nameUUIDFromBytes(("report:" + r.key()).getBytes(StandardCharsets.UTF_8)), "EXPORTED",
                r.title() + ": " + r.rowCount() + " row(s)" + (r.truncated() ? " (cut at " + MAX_ROWS + ")" : "") + (r.asOf() == null ? "" : ", as of " + r.asOf()) + ", " + format, uid, uname);
        byte[] body = switch (format) {
            case CSV -> ("\uFEFF" + csv(r.columns(), r.rows())).getBytes(StandardCharsets.UTF_8);
            case XLSX -> ReportFiles.xlsx(r.title(), ReportRules.subtitle(r.generatedAt(), r.generatedBy(), r.asOf(), r.rowCount(), r.truncated()), r.columns(), r.rows());
            case PDF -> ReportFiles.pdf(r.title(), ReportRules.subtitle(r.generatedAt(), r.generatedBy(), r.asOf(), r.rowCount(), r.truncated()), r.columns(), r.rows());
        };
        return new ExportFile(fileName(r.key(), today(), format.ext), body, format.contentType);
    }

    /**
     * A board pack: the chosen reports in one PDF with a cover page and contents. The same audience rule applies to every report (one the caller may not see is a 404 and
     * the whole pack is refused). A date applies to the reports that can be run as at a date and is ignored by the rest. Each report carries at most
     * ReportRules.PACK_ROWS_PER_REPORT rows and says so when cut. One audit entry names every report in the pack, written in the same transaction as the run.
     */
    @Transactional
    public ExportFile exportPack(TenantId t, List<String> keys, String asOf, String title, UUID uid, String uname, Audience who) {
        List<String> ks = ReportRules.packKeys(keys);
        Map<String, ReportInfo> visibleInfo = new HashMap<>();
        for (ReportInfo i : catalogue(who)) visibleInfo.put(i.key(), i);
        List<ReportFiles.Section> sections = new ArrayList<>();
        for (String key : ks) {
            ReportInfo info = visibleInfo.get(key);
            if (info == null) throw new ResourceNotFoundException("Report", key);
            ReportResult r = run(t, key, info.supportsAsOf() ? asOf : null, uname, who);
            List<List<String>> rows = ReportRules.packCuts(r.rows().size()) ? r.rows().subList(0, ReportRules.PACK_ROWS_PER_REPORT) : r.rows();
            sections.add(new ReportFiles.Section(r.title(), ReportRules.subtitle(r.generatedAt(), r.generatedBy(), r.asOf(), r.rowCount(), r.truncated()) + ReportRules.packNote(r.rows().size()), r.columns(), rows));
        }
        trail.record(t.getValue(), ENTITY, UUID.nameUUIDFromBytes("report:PACK".getBytes(StandardCharsets.UTF_8)), "EXPORTED",
                "Board pack of " + ks.size() + " report(s): " + String.join(", ", ks) + (asOf == null || asOf.isBlank() ? "" : ", as of " + asOf.trim()) + ", PDF", uid, uname);
        byte[] body = ReportFiles.pdfPack(ReportRules.packTitle(title), ReportRules.packSubtitle(today(), uname, ks.size(), asOf), sections);
        return new ExportFile(ReportRules.packFileName(today()), body, ReportFiles.PDF_TYPE);
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

        add(m, "CONTROL_EFFECTIVENESS", "Controls", "Control effectiveness", "Each control with its design rating, tested operating effectiveness, last and next test, and how many obligations and risks it covers. An untested control is shown as not tested, never as effective.", true, false,
                List.of("Reference", "Control", "Type", "Nature", "Frequency", "Owner", "Status", "Design rating", "Operating effectiveness", "Last tested", "Next test due", "Test overdue", "Currently effective", "Obligations", "Risks"),
                c -> controls.list(c.tenant(), null, false).stream().map(x -> List.of(text(x.reference()), text(x.title()), text(x.controlType()), text(x.nature()), text(x.frequency()), text(x.ownerName()), text(x.status()),
                        text(x.designRating()), text(x.operatingEffectiveness()), dateOf(x.lastTestedAt()), date(x.nextTestDue()), yesNo(x.testOverdue()), yesNo(x.currentlyEffective()), num(x.obligationCount()), num(x.riskCount()))).toList());
        add(m, "REG_CHANGES", "Regulatory", "Regulatory changes", "Changes to the regulations you follow, with how many obligations and requirements each affects and how many assessments are still open or overdue.", false, false,
                List.of("Regulation", "From version", "To version", "Kind", "Status", "Added", "Changed", "Removed", "Unchanged", "Impacts", "Impacts pending", "Impacts overdue", "Recorded", "Closed"),
                c -> {
                    Map<UUID, List<ImpactResponse>> byChange = new HashMap<>();
                    for (ImpactResponse i : changes.listImpacts(c.tenant(), null)) byChange.computeIfAbsent(i.changeId(), k -> new ArrayList<>()).add(i);
                    return changes.list(c.tenant(), null, PageRequest.of(0, MAX_ROWS + 1)).getContent().stream().map(x -> {
                        List<ImpactResponse> imp = byChange.getOrDefault(x.id(), List.of());
                        return List.of(text(x.instrumentTitle()), text(x.fromLabel()), text(x.toLabel()), text(x.changeKind()), text(x.status()), num(x.addedCount()), num(x.changedCount()), num(x.removedCount()),
                                num(x.carriedCount()), num(imp.size()), num(imp.stream().filter(i -> i.status() == ImpactStatus.PENDING).count()), num(imp.stream().filter(ImpactResponse::overdue).count()),
                                dateOf(x.createdAt()), dateOf(x.closedAt()));
                    }).toList();
                });
        add(m, "POPIA_ACTIVITIES", "Privacy", "POPIA processing activities", "The register of personal-information processing: category, lawful basis, owner, retention, cross-border transfer and review date.", false, false,
                List.of("Activity", "Data category", "Lawful basis", "Purpose", "Department", "Owner", "Retention", "Cross-border transfer", "Review date", "Review overdue", "Active"),
                c -> popia.list(c.tenant()).stream().map(a -> List.of(text(a.getActivityName()), text(a.getDataCategory()), text(a.getLawfulBasis()), text(a.getPurpose()), text(a.getResponsibleDepartment()), text(a.getResponsibleUserName()),
                        text(a.getRetentionPeriodDescription()), yesNo(a.isCrossBorderTransfer()), date(a.getReviewDate()), yesNo(a.isActive() && a.getReviewDate() != null && a.getReviewDate().isBefore(c.today())), yesNo(a.isActive()))).toList());
        add(m, "DSAR", "Privacy", "Data subject requests", "Requests with type, dates, status and who is handling them. The requester's name and contact details and the resolution notes are not included.", false, false,
                List.of("Request", "Type", "Data category", "Received", "Due", "Status", "Assigned to", "Completed", "Overdue", "Days overdue"),
                c -> dsar.listAll(c.tenant()).stream().map(d -> List.of(text(d.getRequestNumber()), text(d.getRequestType()), text(d.getDataCategory()), date(d.getReceivedDate()), date(d.getDueDate()), text(d.getStatus()),
                        text(d.getAssignedToUserName()), date(d.getCompletedDate()), yesNo(d.isOverdue(c.today())), num(d.isOverdue(c.today()) ? daysOverdue(d.getDueDate(), c.today()) : 0))).toList(), Audience.STAFF);
        add(m, "LITIGATION", "Legal", "Litigation register", "Matters with type, status, court or forum, key dates and estimated exposure. Party names, descriptions and outcome notes are not included. Not shown to external providers.", false, false,
                List.of("Matter", "Type", "Status", "Our side", "Court or forum", "Case reference", "Opened", "Next key date", "Key date passed", "Closed", "Estimated exposure", "Linked contract"),
                c -> litigation.listAll(c.tenant()).stream().map(l -> {
                    boolean live = l.getStatus() == za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus.OPEN || l.getStatus() == za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus.IN_PROGRESS;
                    return List.of(text(l.getMatterNumber()), text(l.getMatterType()), text(l.getStatus()), text(l.getOurSide()), text(l.getCourtOrForum()), text(l.getCaseReference()), date(l.getOpenedDate()), date(l.getNextKeyDate()),
                            yesNo(live && l.getNextKeyDate() != null && l.getNextKeyDate().isBefore(c.today())), date(l.getClosedDate()), text(l.getEstimatedExposure() == null ? null : l.getEstimatedExposure().stripTrailingZeros().toPlainString()), yesNo(l.getLinkedContractId() != null));
                }).toList(), Audience.STAFF);
        add(m, "AUDIT_TRAIL", "Management", "Audit trail", "Who did what, and when, across the compliance registers for the 90 days ending on the chosen date (today if none). Administrators only. FICA, conflict-of-interest, attestation and PAIA activity is never included, and neither is event detail.", true, true,
                List.of("When (SAST)", "Area", "Event", "By"),
                c -> {
                    Instant[] w = AuditTrailRules.window(c.asOf(), c.today());
                    return events.inWindow(c.tenant().getValue(), w[0], w[1], AuditTrailRules.allowedTypes(), PageRequest.of(0, MAX_ROWS + 1)).stream()
                            .map(e -> List.of(AuditTrailRules.stamp(e.getCreatedAt()), text(AuditTrailRules.area(e.getEntityType())), AuditTrailRules.eventLabel(e.getEventType()), text(e.getActorName()))).toList();
                }, Audience.ADMIN);
        return Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, Def> m, String key, String category, String title, String description, boolean names, boolean asOf, List<String> columns, Function<Ctx, List<List<String>>> rows) {
        add(m, key, category, title, description, names, asOf, columns, rows, Audience.PROVIDER);
    }

    private static void add(Map<String, Def> m, String key, String category, String title, String description, boolean names, boolean asOf, List<String> columns, Function<Ctx, List<List<String>>> rows, Audience min) {
        m.put(key, new Def(new ReportInfo(key, category, title, description, names, asOf), columns, rows, min));
    }
}
