package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.internalaudit.application.internal.RiskHeatMap.*;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditUniverseEntryRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.RiskAssessmentRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditHeatMapService {
    private final AuditUniverseEntryRepository universe;
    private final RiskAssessmentRepository risks;

    /** Every active area of the audit universe, placed by its latest assessment. */
    @Transactional(readOnly = true)
    public HeatMap heatMap(TenantId tenant) {
        UUID t = tenant.getValue();
        var entries = universe.findAllActive(t);
        List<AreaIn> areas = entries.stream().map(u -> new AreaIn(u.getId(), u.getName(), u.getProcessArea())).toList();
        List<AssessmentIn> latest = entries.stream()
                .map(u -> risks.findLatestForUniverseEntry(t, u.getId())
                        .map(r -> new AssessmentIn(u.getId(), r.getInherentRiskScore(), r.getControlRiskScore(), r.getFinalAuditRisk())).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return RiskHeatMap.build(areas, latest);
    }
}
