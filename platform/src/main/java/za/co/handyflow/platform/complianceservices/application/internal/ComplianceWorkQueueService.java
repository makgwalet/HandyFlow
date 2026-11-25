package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** One ordered list of what needs attention across the practice's active clients. Read only: it records nothing. */
@Service
@RequiredArgsConstructor
public class ComplianceWorkQueueService {

    private final ComplianceClientRepository clients;
    private final ClientComplianceDeadlineRepository deadlines;
    private final ClientComplianceDocumentRepository documents;
    private final ComplianceClientObligationRepository assignments;
    private final ComplianceObligationRepository obligations;
    private final ComplianceObligationVersionRepository versions;
    private final ComplianceAssessmentRepository assessments;
    private final ComplianceCaseRepository cases;
    private final ComplianceRegChangeRepository regChanges;
    private final ComplianceRegImpactRepository regImpacts;

    @Transactional(readOnly = true)
    public WorkQueueResponse queue(TenantId tenantId, boolean admin, LocalDate today) {
        // inactive clients are not worked on, so they do not show up
        Map<UUID, String> names = clients.findAll(tenantId, "ACTIVE", Pageable.unpaged()).getContent().stream()
                .collect(Collectors.toMap(ComplianceClient::getId, ComplianceClient::getName));
        Map<UUID, ComplianceObligation> obById = obligations.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligation::getId, o -> o));

        List<WorkQueueRules.Item> items = new ArrayList<>();
        for (ClientComplianceDeadline d : deadlines.findPendingDueBy(tenantId, today.plusDays(WorkQueueRules.DEADLINE_SOON_DAYS))) {
            if (!names.containsKey(d.getClientId())) continue;
            String what = d.getDescription() != null && !d.getDescription().isBlank() ? d.getDescription() : d.getDeadlineType();
            items.add(WorkQueueRules.deadline(today, d.getClientId(), names.get(d.getClientId()), what, d.getDueDate()));
        }
        for (ClientComplianceDocument d : documents.findNeedingAttention(tenantId, today.plusDays(WorkQueueRules.DOCUMENT_EXPIRY_DAYS))) {
            if (!names.containsKey(d.getClientId())) continue;
            if (d.getStatus().equals("VERIFIED")) items.add(WorkQueueRules.documentExpiry(today, d.getClientId(), names.get(d.getClientId()), d.getDocumentType(), d.getExpiryDate()));
            else items.add(WorkQueueRules.documentReview(today, d.getClientId(), names.get(d.getClientId()), d.getDocumentType(), d.getCreatedAt()));
        }
        for (ComplianceClientObligation a : assignments.findAwaitingApproval(tenantId)) {
            if (!names.containsKey(a.getClientId())) continue;
            ComplianceObligation o = obById.get(a.getObligationId());
            items.add(WorkQueueRules.approval(a.getClientId(), names.get(a.getClientId()), o == null ? "?" : o.getCode()));
        }
        for (ComplianceObligationVersion v : versions.findInReview(tenantId)) {
            ComplianceObligation o = obById.get(v.getObligationId());
            items.add(WorkQueueRules.catalogueReview(today, o == null ? "?" : o.getCode(), v.getVersionNumber(), v.getSubmittedAt()));
        }
        for (ComplianceAssessment a : assessments.findDraftsForTenant(tenantId)) {
            if (!names.containsKey(a.getClientId())) continue;
            items.add(WorkQueueRules.assessmentOpen(today, a.getClientId(), names.get(a.getClientId()), a.getStartedAt()));
        }
        for (ComplianceCase c : cases.findLiveForTenant(tenantId)) {
            if (!names.containsKey(c.getClientId())) continue;
            items.add(WorkQueueRules.caseItem(today, c.getClientId(), names.get(c.getClientId()), c.getTitle(), c.getStatus(), c.getSeverity(), c.getDueDate()));
        }
        for (ComplianceRegChange c : regChanges.findAwaitingReview(tenantId)) {
            items.add(RegulatoryRules.reviewItem(today, c.getTitle(), c.getStatus(), c.getRaisedAt()));
        }
        List<ComplianceRegImpact> liveImpacts = regImpacts.findLive(tenantId);
        Map<UUID, ComplianceRegChange> changeById = liveImpacts.isEmpty() ? Map.of()
                : regChanges.findByIds(tenantId, liveImpacts.stream().map(ComplianceRegImpact::getChangeId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(ComplianceRegChange::getId, c -> c));
        for (ComplianceRegImpact i : liveImpacts) {
            ComplianceRegChange c = changeById.get(i.getChangeId());
            if (c == null || !names.containsKey(i.getClientId())) continue;
            items.add(RegulatoryRules.impactItem(today, i.getClientId(), names.get(i.getClientId()), c.getTitle(), i.getStatus(), c.getEffectiveDate()));
        }

        List<WorkQueueRules.Item> ordered = WorkQueueRules.order(WorkQueueRules.visibleTo(items.stream().filter(Objects::nonNull).toList(), admin));
        List<QueueEntry> entries = ordered.stream().map(i -> new QueueEntry(i.type(), i.severity(), i.clientId(), i.clientName(), i.title(), i.detail(), i.dueDate(), i.tab())).toList();
        int high = (int) ordered.stream().filter(i -> "HIGH".equals(i.severity())).count();
        int medium = (int) ordered.stream().filter(i -> "MEDIUM".equals(i.severity())).count();
        return new WorkQueueResponse(new Counts(ordered.size(), high, medium, ordered.size() - high - medium), entries);
    }
}
