package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.application.internal.ExternalAuditDashboardBuilder.*;
import za.co.handyflow.platform.auditor.domain.repository.AuditQueryRepository;
import za.co.handyflow.platform.auditor.domain.repository.EvidenceRequestRepository;
import za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalAuditDashboardService {
    private final ExternalAuditEngagementRepository engagements;
    private final EvidenceRequestRepository requests;
    private final AuditQueryRepository queries;

    @Transactional(readOnly = true)
    public Dashboard build(TenantId tenant, UUID engagementId, LocalDate today) {
        UUID t = tenant.getValue();
        return ExternalAuditDashboardBuilder.build(
                engagements.findByTenantIdOrderByCreatedAtDesc(t).stream().map(e -> new EngagementIn(e.getId(), e.getName(), e.getStatus())).toList(),
                requests.findByTenantIdOrderByCreatedAtDesc(t).stream()
                        .map(r -> new RequestIn(r.getEngagementId(), r.getReference(), r.getTitle(), r.getStatus(), r.getDueDate(), r.getTimesRejected())).toList(),
                queries.findByTenantIdOrderByCreatedAtDesc(t).stream()
                        .map(q -> new QueryIn(q.getEngagementId(), q.getKind(), q.getReference(), q.getTitle(), q.getStatus(), q.getDueDate(), q.getTimesReopened())).toList(),
                engagementId, today);
    }
}
