package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.dto.SourceEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Works out leads, wins and win rate per source. Pure logic, no database. */
public final class SourceReport {

    /** One lead as the report sees it. */
    public record Lead(LeadSource source, LeadStage stage, BigDecimal dealValue) {}

    private SourceReport() {}

    public static List<SourceEntry> build(List<Lead> leads) {
        Map<LeadSource, List<Lead>> bySource = new EnumMap<>(LeadSource.class);
        List<Lead> unknown = new ArrayList<>();
        for (Lead l : leads) {
            if (l.source() == null) unknown.add(l);
            else bySource.computeIfAbsent(l.source(), k -> new ArrayList<>()).add(l);
        }
        List<SourceEntry> out = new ArrayList<>();
        bySource.forEach((s, ls) -> out.add(entry(s, ls)));
        if (!unknown.isEmpty()) out.add(entry(null, unknown));
        // Most leads first; the "not recorded" row goes last on a tie so real sources lead.
        out.sort(Comparator.comparingLong(SourceEntry::leads).reversed().thenComparing(e -> e.source() == null));
        return out;
    }

    private static SourceEntry entry(LeadSource source, List<Lead> ls) {
        long won = 0, lost = 0;
        BigDecimal wonValue = BigDecimal.ZERO;
        for (Lead l : ls) {
            if (l.stage() == LeadStage.WON) {
                won++;
                if (l.dealValue() != null) wonValue = wonValue.add(l.dealValue());
            } else if (l.stage() == LeadStage.LOST) {
                lost++;
            }
        }
        BigDecimal rate = won + lost == 0 ? null
                : BigDecimal.valueOf(won).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(won + lost), 1, RoundingMode.HALF_UP);
        return new SourceEntry(source, ls.size(), won, lost, ls.size() - won - lost, rate, wonValue);
    }
}
