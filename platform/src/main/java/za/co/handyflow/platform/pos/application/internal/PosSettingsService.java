package za.co.handyflow.platform.pos.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.pos.domain.model.PosSettings;
import za.co.handyflow.platform.pos.domain.repository.PosSettingsRepository;
import za.co.handyflow.platform.pos.dto.PosSettingsResponse;
import za.co.handyflow.platform.pos.dto.UpdatePosSettingsRequest;
import za.co.handyflow.platform.shared.TenantId;

@Service
@RequiredArgsConstructor
public class PosSettingsService {

    private final PosSettingsRepository settingsRepo;

    // Lazy-create on first read/write — avoids needing a hook into module
    // activation just to seed one row of sane defaults. Every existing
    // tenant, and every new one, gets defaults() the first time anything
    // asks — same "create on first miss" shape as HrService's leave
    // balance seeding.
    @Transactional
    public PosSettings getOrCreate(TenantId tenantId) {
        return settingsRepo.findByTenantId(tenantId)
                .orElseGet(() -> settingsRepo.save(PosSettings.defaults(tenantId)));
    }

    @Transactional(readOnly = true)
    public PosSettingsResponse getSettings(TenantId tenantId) {
        return toResponse(getOrCreate(tenantId));
    }

    @Transactional
    public PosSettingsResponse updateSettings(TenantId tenantId, UpdatePosSettingsRequest req) {
        PosSettings s = getOrCreate(tenantId);
        s.update(req.cashVarianceToleranceAmount(), req.cashVarianceTolerancePct(),
                req.cashVarianceCriticalAmount(), req.cashVarianceCriticalPct());
        settingsRepo.save(s);
        return toResponse(s);
    }

    private PosSettingsResponse toResponse(PosSettings s) {
        return new PosSettingsResponse(s.getCashVarianceToleranceAmount(), s.getCashVarianceTolerancePct(),
                s.getCashVarianceCriticalAmount(), s.getCashVarianceCriticalPct());
    }
}