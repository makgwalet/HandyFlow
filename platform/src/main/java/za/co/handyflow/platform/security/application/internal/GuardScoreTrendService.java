// security/application/internal/GuardScoreTrendService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.HistoryPoint;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** The daily score snapshots of one guard, oldest first, for the trend on the Performance tab. */
@Service
@RequiredArgsConstructor
public class GuardScoreTrendService {

    static final int DEFAULT_DAYS = 90;
    static final int MAX_DAYS = 365;

    private final GuardService guardService;
    private final GuardScoreHistoryStore store;

    @Transactional(readOnly = true)
    public List<HistoryPoint> history(TenantId tenantId, UUID guardId, Integer days) {
        guardService.getGuard(tenantId, guardId);
        int n = days == null ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
        return store.history(tenantId, guardId, n).stream()
                .map(s -> new HistoryPoint(s.date(), s.score(), s.band(), s.coverage(), recommendationCount(s.recommendations())))
                .toList();
    }

    static int recommendationCount(String encoded) {
        if (encoded == null || encoded.isBlank()) return 0;
        return (int) java.util.Arrays.stream(encoded.split(",")).filter(x -> !x.isBlank()).count();
    }
}
