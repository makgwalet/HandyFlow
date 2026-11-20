package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.PostureSnapshot;
import za.co.handyflow.platform.legalcompliance.domain.repository.PostureSnapshotRepository;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.Forecast;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.Indicator;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * Indicative trends from the stored snapshots. Uses each snapshot's own counts through {@link ExecutiveRules#indicators}, so an indicator introduced later
 * simply has fewer points. Read-only; nothing is stored. See {@link ForecastRules} for what the numbers do and do not mean.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveForecastService {

    private final PostureSnapshotRepository snapshots;

    @Transactional(readOnly = true)
    public List<Forecast> forecast(TenantId t, int months) {
        int m = Math.max(3, Math.min(months, 60));
        List<PostureSnapshot> history = snapshots.history(t, LocalDate.now().minusMonths(m));
        Map<String, List<ForecastRules.Point>> series = new LinkedHashMap<>();
        Map<String, Indicator> meta = new LinkedHashMap<>();
        for (PostureSnapshot s : history)
            for (Indicator i : ExecutiveRules.indicators(s.getMetrics())) {
                meta.put(i.key(), i);
                if (i.percent() != null) series.computeIfAbsent(i.key(), k -> new ArrayList<>()).add(new ForecastRules.Point(s.getSnapshotDate(), i.percent()));
            }
        List<Forecast> out = new ArrayList<>();
        for (var e : meta.entrySet()) {
            List<ForecastRules.Point> pts = series.getOrDefault(e.getKey(), List.of());
            ForecastRules.Fit f = ForecastRules.fit(pts);
            ForecastRules.Point latest = pts.stream().max(Comparator.comparing(ForecastRules.Point::date)).orElse(null);
            out.add(new Forecast(e.getKey(), e.getValue().label(), e.getValue().tab(), f.points(), latest == null ? null : latest.percent(), latest == null ? null : latest.date(),
                    f.direction().name(), f.slopePer30Days(), f.r2(), f.projected30(), f.projected90(), f.note()));
        }
        return out;
    }
}
