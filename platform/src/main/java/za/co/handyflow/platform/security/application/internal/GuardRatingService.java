// security/application/internal/GuardRatingService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.GuardRating;
import za.co.handyflow.platform.security.domain.repository.GuardRatingRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.RatingItem;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.SaveRatingRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Records and lists guard ratings. A rating is a record of what someone said on a date; it is never edited or deleted. */
@Service
@RequiredArgsConstructor
public class GuardRatingService {

    public static final int WINDOW_DAYS = 180;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardRatingRepository repository;
    private final GuardService guardService;
    private final SiteRepository siteRepository;

    @Transactional
    public RatingItem add(TenantId tenantId, UUID guardId, SaveRatingRequest req, UUID by, String byName) {
        return add(tenantId, guardId, req, by, byName, LocalDate.now(SAST));
    }

    @Transactional
    RatingItem add(TenantId tenantId, UUID guardId, SaveRatingRequest req, UUID by, String byName, LocalDate today) {
        guardService.getGuard(tenantId, guardId);
        if (req.siteId() != null) siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", req.siteId().toString()));
        GuardRating.Source source;
        try { source = GuardRating.Source.valueOf(req.source().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw bad("Unknown rating source: " + req.source(), "INVALID_SOURCE"); }
        if (req.ratedOn().isAfter(today)) throw bad("The rating date cannot be in the future", "INVALID_DATE");
        int[] scores = {req.punctuality(), req.professionalism(), req.appearance(), req.communication(), req.alertness(), req.incidentHandling()};
        for (int s : scores) if (s < 1 || s > 5) throw bad("Each score must be from 1 to 5", "INVALID_SCORE");
        GuardRating saved = repository.save(GuardRating.create(tenantId, guardId, req.siteId(), source, req.raterName(), req.ratedOn(), scores, req.comment(), by, byName));
        return toItem(saved, new HashMap<>(), tenantId);
    }

    @Transactional(readOnly = true)
    public List<RatingItem> recent(TenantId tenantId, UUID guardId, LocalDate today) {
        Map<UUID, String> sites = new HashMap<>();
        return repository.findForGuardSince(tenantId, guardId, today.minusDays(WINDOW_DAYS)).stream().map(r -> toItem(r, sites, tenantId)).toList();
    }

    private RatingItem toItem(GuardRating r, Map<UUID, String> sites, TenantId tenantId) {
        String site = r.getSiteId() == null ? null
                : sites.computeIfAbsent(r.getSiteId(), k -> siteRepository.findActiveById(tenantId, k).map(s -> s.getName()).orElse(null));
        return new RatingItem(r.getId(), r.getSource().name(), r.getRaterName(), r.getSiteId(), site, r.getRatedOn(),
                r.getPunctuality(), r.getProfessionalism(), r.getAppearance(), r.getCommunication(), r.getAlertness(), r.getIncidentHandling(),
                Math.round(r.average() * 10) / 10.0, r.getComment(), r.getCreatedByName(), r.getCreatedAt());
    }

    private static HandyFlowException bad(String message, String code) { return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code); }
}
