package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.dto.TargetProgressRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sets each salesperson's wins in a month against their target. Pure logic, no database. */
public final class TargetProgress {

    /** A deal that was won: who owns it, what it was worth, and when it was marked won. */
    public record Win(UUID ownerId, BigDecimal dealValue, Instant wonAt) {}

    /** A target as stored. */
    public record Target(UUID ownerId, BigDecimal amount) {}

    private TargetProgress() {}

    /**
     * One row for everyone who has a target or a win in the month, most achieved first. Wins with no owner are not counted
     * (nobody to credit). A win counts in the month it was marked won, in the given time zone.
     */
    public static List<TargetProgressRow> build(YearMonth month, ZoneId zone, List<Win> wins, List<Target> targets) {
        Map<UUID, BigDecimal> target = new LinkedHashMap<>();
        for (Target t : targets) target.put(t.ownerId(), t.amount());
        Map<UUID, BigDecimal> achieved = new LinkedHashMap<>();
        Map<UUID, Long> count = new LinkedHashMap<>();
        for (Win w : wins) {
            if (w.ownerId() == null || w.wonAt() == null) continue;
            if (!YearMonth.from(w.wonAt().atZone(zone)).equals(month)) continue;
            achieved.merge(w.ownerId(), w.dealValue() == null ? BigDecimal.ZERO : w.dealValue(), BigDecimal::add);
            count.merge(w.ownerId(), 1L, Long::sum);
        }
        Map<UUID, Boolean> owners = new LinkedHashMap<>();
        target.keySet().forEach(o -> owners.put(o, true));
        achieved.keySet().forEach(o -> owners.put(o, true));
        List<TargetProgressRow> rows = new ArrayList<>();
        for (UUID o : owners.keySet()) {
            BigDecimal tg = target.get(o), got = achieved.getOrDefault(o, BigDecimal.ZERO);
            BigDecimal pct = tg == null ? null : got.multiply(BigDecimal.valueOf(100)).divide(tg, 1, RoundingMode.HALF_UP);
            rows.add(new TargetProgressRow(o, tg, got, count.getOrDefault(o, 0L), pct));
        }
        rows.sort(Comparator.comparing(TargetProgressRow::achieved).reversed().thenComparing(r -> r.ownerId().toString()));
        return rows;
    }
}
