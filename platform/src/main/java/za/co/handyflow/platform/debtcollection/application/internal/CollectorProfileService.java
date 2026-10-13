package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorProfile;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;
import za.co.handyflow.platform.debtcollection.domain.repository.CollectorProfileRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Registers collectors (profile per platform user). Upsert by userId; deactivating keeps the profile and its history. */
@Service
@RequiredArgsConstructor
public class CollectorProfileService {

    private final CollectorProfileRepository repository;

    @Transactional
    public CollectorProfile upsert(TenantId tenantId, UUID userId, String userName, String team, CollectorTier tier,
                                   Set<String> skills, Integer maxOpenCases, BigDecimal maxCaseValue,
                                   BigDecimal maxPortfolioValue, LocalDate awayUntil) {
        var existing = repository.findByUserId(tenantId, userId);
        if (existing.isPresent()) {
            existing.get().update(userName, team, tier, skills, maxOpenCases, maxCaseValue, maxPortfolioValue, awayUntil);
            return repository.save(existing.get());
        }
        return repository.save(CollectorProfile.create(tenantId, userId, userName, team, tier, skills, maxOpenCases,
                maxCaseValue, maxPortfolioValue, awayUntil));
    }

    @Transactional
    public CollectorProfile setActive(TenantId tenantId, UUID userId, boolean active) {
        CollectorProfile p = repository.findByUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CollectorProfile", userId.toString()));
        if (active) p.activate(); else p.deactivate();
        return repository.save(p);
    }

    @Transactional(readOnly = true)
    public List<CollectorProfile> list(TenantId tenantId) {
        return repository.findAllForTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public CollectorProfile get(TenantId tenantId, UUID userId) {
        return repository.findByUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CollectorProfile", userId.toString()));
    }
}
