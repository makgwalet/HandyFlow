package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgProfitabilityRules;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.FarmMargin;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityOverviewResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Subtotal;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Totals;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.UnitProfit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Puts every farm's gross-margin report side by side (ADR-001, W6b). Pure: no repositories.
 * <p>
 * It never calculates a margin itself. Each farm's figures come from that farm's own {@link ProfitabilityResponse}, and the overview is their exact SUM, so it cannot
 * disagree with a farm's Profitability screen. Percentages are recomputed from the sums (a farm with a tiny revenue must not drag an average); nothing is averaged.
 */
public final class AgProfitabilityOverviewAggregator {

    private AgProfitabilityOverviewAggregator() {}

    /** A farm and its own report. */
    public record FarmReport(UUID farmId, String farmName, ProfitabilityResponse report) {}

    /** More farm warnings than this are summarised, so the notes stay readable on a tenant with many farms. */
    static final int MAX_FARM_NOTES = 30;

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static ProfitabilityOverviewResponse build(List<FarmReport> reports, List<String> extraNotes) {
        BigDecimal rev = BigDecimal.ZERO, rec = BigDecimal.ZERO, lab = BigDecimal.ZERO, eq = BigDecimal.ZERO, fu = BigDecimal.ZERO, oth = BigDecimal.ZERO;
        int[] units = new int[3];
        BigDecimal[] sRev = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}, sCost = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        List<FarmMargin> farms = new ArrayList<>();
        List<String> farmNotes = new ArrayList<>();

        List<FarmReport> sorted = new ArrayList<>(reports);
        sorted.sort(Comparator.comparing((FarmReport r) -> r.farmName() == null ? "" : r.farmName().toLowerCase()));
        for (FarmReport fr : sorted) {
            ProfitabilityResponse p = fr.report();
            Totals t = p.totals();
            rev = rev.add(t.revenue()); rec = rec.add(t.recordedCost()); lab = lab.add(t.labour()); eq = eq.add(t.equipment()); fu = fu.add(t.fuel()); oth = oth.add(t.otherDirect());
            Subtotal[] subs = {p.complete(), p.inProgress(), p.breedingStock()};
            for (int i = 0; i < 3; i++) { units[i] += subs[i].units(); sRev[i] = sRev[i].add(subs[i].revenue()); sCost[i] = sCost[i].add(subs[i].directCost()); }

            int caveatUnits = (int) p.units().stream().filter((UnitProfit u) -> !u.caveats().isEmpty()).count();
            List<String> specific = p.notes().stream().filter(n -> !AgProfitabilityOverviewAggregator.isStandard(n)).toList();
            for (String n : specific) farmNotes.add(fr.farmName() + ": " + n);
            farms.add(new FarmMargin(fr.farmId(), fr.farmName(), money(t.revenue()), money(t.directCost()), money(t.grossMargin()), t.marginPercent(),
                    subs[0].units(), subs[1].units(), subs[2].units(), caveatUnits + specific.size()));
        }

        BigDecimal direct = rec.add(lab).add(eq).add(fu).add(oth);
        BigDecimal margin = AgProfitabilityRules.margin(rev, direct);
        Totals totals = new Totals(money(rev), money(rec), money(lab), money(eq), money(fu), money(oth), money(direct), margin, AgProfitabilityRules.marginPercent(rev, margin));

        List<String> notes = new ArrayList<>();
        notes.add(AgProfitabilityAggregator.STANDARD_NOTE);
        for (int i = 0; i < farmNotes.size() && i < MAX_FARM_NOTES; i++) notes.add(farmNotes.get(i));
        if (farmNotes.size() > MAX_FARM_NOTES) notes.add((farmNotes.size() - MAX_FARM_NOTES) + " more warning(s) on individual farms; open each farm's Profitability screen for them.");
        notes.addAll(extraNotes);

        return new ProfitabilityOverviewResponse(totals, sub(units[0], sRev[0], sCost[0]), sub(units[1], sRev[1], sCost[1]), sub(units[2], sRev[2], sCost[2]), List.copyOf(farms), List.copyOf(notes));
    }

    private static boolean isStandard(String note) { return AgProfitabilityAggregator.STANDARD_NOTE.equals(note); }

    private static Subtotal sub(int units, BigDecimal revenue, BigDecimal directCost) {
        BigDecimal margin = AgProfitabilityRules.margin(revenue, directCost);
        return new Subtotal(units, money(revenue), money(directCost), margin, AgProfitabilityRules.marginPercent(revenue, margin));
    }
}
