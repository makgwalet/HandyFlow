package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustSettingChange;
import za.co.handyflow.platform.legalpractice.domain.repository.LpTrustSettingChangeRepository;
import za.co.handyflow.platform.legalpractice.dto.LpTrustSettingsResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** The firm's trust control setting. Strict by default; every relaxation or tightening is a permanent, explained record. */
@Service
@RequiredArgsConstructor
public class LpTrustSettingsService {

    private final LpTrustSettingChangeRepository repo;

    /** May one person request and approve their own trust payment? False until an administrator says otherwise, in writing. */
    @Transactional(readOnly = true)
    public boolean selfApprovalAllowed(TenantId tenantId) {
        return repo.current(tenantId).map(LpTrustSettingChange::isSelfApprovalAllowed).orElse(false);
    }

    @Transactional(readOnly = true)
    public LpTrustSettingsResponse get(TenantId tenantId) {
        var history = repo.latest(tenantId, PageRequest.of(0, 20)).stream()
                .map(c -> new LpTrustSettingsResponse.Change(c.isSelfApprovalAllowed(), c.getReason(), c.getChangedByName(), c.getChangedAt()))
                .toList();
        return new LpTrustSettingsResponse(selfApprovalAllowed(tenantId), history);
    }

    @Transactional
    public LpTrustSettingsResponse update(TenantId tenantId, boolean allowed, String reason, UUID by, String byName) {
        if (allowed == selfApprovalAllowed(tenantId)) {
            throw new IllegalStateException("That is already the current setting");
        }
        repo.save(LpTrustSettingChange.record(tenantId, allowed, reason, by, byName));
        return get(tenantId);
    }
}
