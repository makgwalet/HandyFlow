package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.GuardCompetency;
import za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.CompetencyReadinessResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Tells a supervisor whether it is safe to set handyflow.security.armoury.strict-competency=true. */
@Service
@RequiredArgsConstructor
public class CompetencyReadinessService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardRepository guardRepository;
    private final GuardCompetencyRepository competencyRepository;

    @Value("${handyflow.security.armoury.strict-competency:false}")
    private boolean strictEnabled;

    @Transactional(readOnly = true)
    public CompetencyReadinessResponse report(TenantId tenantId) {
        return report(tenantId, LocalDate.now(SAST));
    }

    CompetencyReadinessResponse report(TenantId tenantId, LocalDate today) {
        Map<UUID, List<GuardCompetency>> byGuard = competencyRepository
                .findByTypeForTenant(tenantId, GuardCompetency.Type.FIREARM_COMPETENCY).stream()
                .collect(Collectors.groupingBy(GuardCompetency::getGuardId));

        List<CompetencyReadinessResponse.Row> rows = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StrictReadiness.Status s : StrictReadiness.Status.values()) counts.put(s.name(), 0);
        int blocked = 0;

        for (Guard g : guardRepository.findAllActiveList(tenantId)) {
            if (!g.isSchedulable()) continue;                   // suspended or terminated guards are not issued firearms anyway
            List<GuardCompetency> mine = byGuard.getOrDefault(g.getId(), List.of());
            boolean legacy = g.hasFirearmCompetency();
            if (mine.isEmpty() && !legacy) continue;            // carries no firearms: not part of this question
            var certs = mine.stream()
                    .map(c -> new StrictReadiness.Cert(c.getFirearmCategory(), c.getExpiryDate(), c.isVerified()))
                    .toList();
            var r = StrictReadiness.classify(certs, legacy, today);
            counts.merge(r.status().name(), 1, Integer::sum);
            if (StrictReadiness.blockedByStrict(r.status())) blocked++;
            rows.add(new CompetencyReadinessResponse.Row(g.getId(), g.getFullName(), g.getEmployeeCode(),
                    r.status().name(), List.copyOf(r.covered()), r.hint()));
        }
        rows.sort(Comparator.comparing((CompetencyReadinessResponse.Row r) -> !StrictReadiness.blockedByStrict(
                        StrictReadiness.Status.valueOf(r.status())))
                .thenComparing(r -> r.fullName() == null ? "" : r.fullName()));
        return new CompetencyReadinessResponse(strictEnabled, blocked == 0, blocked, counts, rows);
    }
}
