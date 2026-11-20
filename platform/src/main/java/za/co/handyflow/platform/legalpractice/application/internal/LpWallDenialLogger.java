package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpWallDenial;
import za.co.handyflow.platform.legalpractice.domain.repository.LpWallDenialRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a refusal in its own transaction: the refused request is rolled back, and the record of the refusal must not be.
 * A separate bean so the new transaction really starts.
 */
@Component
@RequiredArgsConstructor
public class LpWallDenialLogger {

    private final LpWallDenialRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TenantId tenantId, UUID userId, String userName, UUID matterId, UUID clientId, UUID wallId) {
        repo.save(LpWallDenial.of(tenantId, userId, userName, matterId, clientId, wallId, Instant.now()));
    }
}
