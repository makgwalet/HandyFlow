package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.internalaudit.application.internal.EngagementSummaryBuilder.*;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEngagementSummaryService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final AuditEngagementRepository engagements;
    private final EngagementAssignmentRepository assignments;
    private final AuditWorkpaperFolderRepository folders;
    private final AuditWorkpaperFileRepository files;
    private final AuditProgrammeStepRepository steps;
    private final SamplingPlanRepository samplingPlans;
    private final SampleItemRepository sampleItems;
    private final AuditTestRepository tests;
    private final AuditReportRepository reports;
    private final AuditFindingRepository findings;
    private final AuditActionPlanRepository actions;

    /** Every current workpaper of the engagement in one query, for the workpapers list. */
    @Transactional(readOnly = true)
    public List<za.co.handyflow.platform.internalaudit.dto.WorkpaperFileResponse> workpapers(TenantId tenant, UUID engagementId) {
        UUID t = tenant.getValue();
        engagements.findByTenantAndId(t, engagementId).orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        return files.currentForEngagement(t, engagementId);
    }

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenant, UUID engagementId, LocalDate today) {
        UUID t = tenant.getValue();
        AuditEngagement e = engagements.findByTenantAndId(t, engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));

        int team = assignments.findByEngagement(t, engagementId).size();
        PlanningIn planning = new PlanningIn(filled(e.getObjectives()), filled(e.getScope()), filled(e.getAuditCriteria()), e.getOverallMateriality() != null, team > 0);

        // Current versions only: a file replaced by a newer version no longer counts.
        var wps = files.currentForEngagement(t, engagementId);
        int signedOff = (int) wps.stream().filter(f -> "SIGNED_OFF".equals(f.reviewStatus())).count();

        List<AuditProgrammeStep> ps = steps.findByTenantIdAndEngagementIdOrderByControlCodeAsc(t, engagementId);
        int stepsDone = (int) ps.stream().filter(s -> "COMPLETED".equals(s.getStatus()) || "NOT_APPLICABLE".equals(s.getStatus())).count();

        int sampled = 0, tested = 0;
        for (SamplingPlan sp : samplingPlans.findByEngagement(t, engagementId)) {
            for (SampleItem si : sampleItems.findByPlan(t, sp.getId())) {
                sampled++;
                if (tests.findBySampleItem(t, si.getId()).stream().anyMatch(x -> !"PENDING".equals(x.getResult()))) tested++;
            }
        }

        AuditReport latest = reports.findByTenantIdAndEngagementIdOrderByVersionDesc(t, engagementId).stream().findFirst().orElse(null);
        ReportIn report = latest == null ? null : new ReportIn(latest.getStatus(), day(latest.getCreatedAt()), latest.getIssuedAt() == null ? null : day(latest.getIssuedAt()));

        List<AuditFinding> fs = findings.findByEngagement(t, engagementId);
        int open = (int) fs.stream().filter(f -> InternalAuditDashboardBuilder.isOpen(f.getStatus())).count();
        int pastDue = (int) fs.stream().filter(f -> InternalAuditDashboardBuilder.isOpen(f.getStatus()) && f.getDueDate() != null && f.getDueDate().isBefore(today)).count();
        int aTotal = 0, aValidated = 0, aOverdue = 0;
        for (AuditFinding f : fs) {
            for (AuditActionPlan a : actions.findByFinding(t, f.getId())) {
                if ("CANCELLED".equals(a.getStatus())) continue;
                aTotal++;
                if ("VALIDATED".equals(a.getStatus())) aValidated++;
                if (a.isOverdue(today)) aOverdue++;
            }
        }
        return EngagementSummaryBuilder.build(new Inputs(e.getStatus(), e.getStartDate(), e.getEndDate(), day(e.getCreatedAt()), planning,
                wps.size(), signedOff, ps.size(), stepsDone, sampled, tested, report, fs.size(), open, pastDue, aTotal, aValidated, aOverdue, team), today);
    }

    private static boolean filled(String s) { return s != null && !s.isBlank(); }
    private static LocalDate day(java.time.Instant i) { return i.atZone(ZONE).toLocalDate(); }
}
