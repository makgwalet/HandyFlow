package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService.AgingBucket;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService.AgingReport;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.ExposureSnapshot;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.ExposureSnapshotRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Takes (or overwrites) one tenant's exposure snapshot for a day, from the same aging calculation the dashboard uses. */
@Service
@RequiredArgsConstructor
public class ExposureSnapshotService {

    private static final Set<CaseStatus> TERMINAL = EnumSet.of(CaseStatus.CLOSED, CaseStatus.WRITTEN_OFF, CaseStatus.SETTLED);

    private final DebtCollectionDashboardService dashboardService;
    private final DebtCollectionCaseRepository caseRepository;
    private final ExposureSnapshotRepository repository;

    @Transactional
    public ExposureSnapshot snapshot(TenantId tenantId, LocalDate date) {
        AgingReport aging = dashboardService.aging(tenantId);
        List<AgingBucket> b = aging.buckets();
        BigDecimal[] buckets = new BigDecimal[5];
        for (int i = 0; i < 5; i++) buckets[i] = b.get(i).amount();
        int active = (int) caseRepository.findAllActive(tenantId).stream().filter(c -> !TERMINAL.contains(c.getStatus())).count();
        ExposureSnapshot existing = repository.findByDate(tenantId, date).orElse(null);
        if (existing != null) {
            existing.apply(aging.totalOutstanding(), active, buckets);
            return repository.save(existing);
        }
        return repository.save(ExposureSnapshot.of(tenantId, date, aging.totalOutstanding(), active, buckets));
    }
}
