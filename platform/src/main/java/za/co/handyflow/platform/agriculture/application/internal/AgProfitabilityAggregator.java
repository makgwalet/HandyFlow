package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgProfitabilityRules;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Subtotal;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.Totals;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.UnitProfit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the gross-margin report from data the service has already loaded (ADR-001, W5). Pure: no repositories, no clock.
 * <p>
 * Nothing is counted twice because every cost and every sale points at exactly ONE target: the existing feed, health, seed and input costs belong
 * to an animal, group or crop cycle; each cost-ledger row and each sale allocation names one target. A unit's cost is its recorded cost plus its
 * ledger rows, and the farm total is the exact sum of the unit rows. A cost or sale on a target that is no longer listed (deleted since) goes in one
 * catch-all row rather than vanishing, so the totals always equal everything in the ledger and in sales.
 */
public final class AgProfitabilityAggregator {

    private AgProfitabilityAggregator() {}

    public static final String UNLISTED = "UNLISTED";

    /** Always the first note of a report; the overview leaves it out of the per-farm warnings because it applies to everything. */
    public static final String STANDARD_NOTE = "Gross margin only: revenue (ex-VAT, net of credit notes) minus direct production costs. Overheads, finance costs, depreciation and tax are not included.";

    /** A unit as the service found it. {@code recordedCost} is what the existing reports already count (feed, health, seed, inputs, purchase price). */
    public record UnitInput(String targetType, UUID id, String label, String status, String acquisitionType, BigDecimal recordedCost, boolean breedingStock) {
        /** A unit that is not breeding stock. */
        public UnitInput(String targetType, UUID id, String label, String status, String acquisitionType, BigDecimal recordedCost) {
            this(targetType, id, label, status, acquisitionType, recordedCost, false);
        }
    }

    /** One net ledger total for a target and category (reversals already netted). */
    public record LedgerRow(String targetType, UUID targetId, String category, BigDecimal amount) {}

    /** One target's revenue: ex-VAT, net of credit notes. */
    public record RevenueRow(String targetType, UUID targetId, BigDecimal revenue) {}

    private static final class Acc {
        BigDecimal revenue = BigDecimal.ZERO, labour = BigDecimal.ZERO, equipment = BigDecimal.ZERO, fuel = BigDecimal.ZERO, other = BigDecimal.ZERO;
    }

    private static String key(String type, UUID id) { return type + ":" + id; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static ProfitabilityResponse build(UUID farmId, List<UnitInput> units, List<LedgerRow> ledger, List<RevenueRow> revenue,
                                              int uncostedLabour, int notCountedSales, List<String> extraNotes) {
        Map<String, Acc> money = new HashMap<>();
        for (LedgerRow r : ledger) {
            Acc a = money.computeIfAbsent(key(r.targetType(), r.targetId()), k -> new Acc());
            switch (r.category() == null ? "" : r.category()) {
                case "LABOUR" -> a.labour = a.labour.add(nz(r.amount()));
                case "EQUIPMENT" -> a.equipment = a.equipment.add(nz(r.amount()));
                case "FUEL" -> a.fuel = a.fuel.add(nz(r.amount()));
                default -> a.other = a.other.add(nz(r.amount()));       // OTHER_DIRECT and any category not known here: never dropped
            }
        }
        for (RevenueRow r : revenue) {
            Acc a = money.computeIfAbsent(key(r.targetType(), r.targetId()), k -> new Acc());
            a.revenue = a.revenue.add(nz(r.revenue()));
        }

        List<UnitProfit> rows = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (UnitInput u : units) {
            String k = key(u.targetType(), u.id());
            listed.add(k);
            Acc a = money.getOrDefault(k, new Acc());
            String state = u.breedingStock() ? AgProfitabilityRules.BREEDING_STOCK : AgProfitabilityRules.stateOf(u.targetType(), u.status());
            UnitProfit row = row(u.targetType(), u.id(), u.label(), u.status(), state, nz(u.recordedCost()), a,
                    AgProfitabilityRules.caveats(u.targetType(), u.status(), u.acquisitionType(), a.revenue, u.breedingStock()));
            if (!isEmpty(row)) rows.add(row);                         // a unit with no money in or out is just noise
        }

        Acc orphan = new Acc();
        boolean anyOrphan = false;
        for (Map.Entry<String, Acc> e : money.entrySet()) {
            if (listed.contains(e.getKey())) continue;
            Acc a = e.getValue();
            orphan.revenue = orphan.revenue.add(a.revenue); orphan.labour = orphan.labour.add(a.labour); orphan.equipment = orphan.equipment.add(a.equipment);
            orphan.fuel = orphan.fuel.add(a.fuel); orphan.other = orphan.other.add(a.other);
            anyOrphan = true;
        }
        if (anyOrphan) {
            UnitProfit row = row(UNLISTED, null, "Removed or unlisted targets", null, AgProfitabilityRules.IN_PROGRESS, BigDecimal.ZERO, orphan,
                    List.of("These costs and sales belong to targets that are no longer listed (for example, deleted since). They are kept here so the totals stay complete."));
            if (!isEmpty(row)) rows.add(row);
        }

        rows.sort(Comparator.comparing((UnitProfit r) -> AgProfitabilityRules.COMPLETE.equals(r.state()) ? 0 : AgProfitabilityRules.IN_PROGRESS.equals(r.state()) ? 1 : 2)
                .thenComparing(UnitProfit::targetType).thenComparing(r -> r.label() == null ? "" : r.label().toLowerCase()));

        List<String> notes = new ArrayList<>();
        notes.add(STANDARD_NOTE);
        if (uncostedLabour > 0) notes.add(uncostedLabour + " recorded labour entr" + (uncostedLabour == 1 ? "y isn't" : "ies aren't") + " costed yet, so labour is understated until " + (uncostedLabour == 1 ? "it is" : "they are") + " costed (Insights > Labour).");
        if (notCountedSales > 0) notes.add(notCountedSales + " sale allocation" + (notCountedSales == 1 ? " isn't" : "s aren't") + " counted (for example, the invoice was cancelled), so revenue excludes " + (notCountedSales == 1 ? "it" : "them") + ".");

        if (rows.stream().anyMatch(r -> AgProfitabilityRules.BREEDING_STOCK.equals(r.state()))) {
            notes.add("Breeding stock is shown apart from the production margins. Its purchase prices are capital and aren't counted, but its running costs and any sales are in the farm totals.");
        }
        notes.addAll(extraNotes);

        return new ProfitabilityResponse(farmId, totals(rows), subtotal(rows, AgProfitabilityRules.COMPLETE), subtotal(rows, AgProfitabilityRules.IN_PROGRESS), subtotal(rows, AgProfitabilityRules.BREEDING_STOCK), List.copyOf(rows), List.copyOf(notes));
    }

    private static UnitProfit row(String type, UUID id, String label, String status, String state, BigDecimal recorded, Acc a, List<String> caveats) {
        BigDecimal direct = recorded.add(a.labour).add(a.equipment).add(a.fuel).add(a.other);
        BigDecimal margin = AgProfitabilityRules.margin(a.revenue, direct);
        return new UnitProfit(type, id, label, status, state, money(a.revenue), money(recorded), money(a.labour), money(a.equipment), money(a.fuel), money(a.other),
                money(direct), margin, AgProfitabilityRules.marginPercent(a.revenue, margin), List.copyOf(caveats));
    }

    private static boolean isEmpty(UnitProfit r) {
        return r.revenue().signum() == 0 && r.directCost().signum() == 0;
    }

    private static Totals totals(List<UnitProfit> rows) {
        BigDecimal rev = BigDecimal.ZERO, rec = BigDecimal.ZERO, lab = BigDecimal.ZERO, eq = BigDecimal.ZERO, fu = BigDecimal.ZERO, oth = BigDecimal.ZERO;
        for (UnitProfit r : rows) { rev = rev.add(r.revenue()); rec = rec.add(r.recordedCost()); lab = lab.add(r.labour()); eq = eq.add(r.equipment()); fu = fu.add(r.fuel()); oth = oth.add(r.otherDirect()); }
        BigDecimal direct = rec.add(lab).add(eq).add(fu).add(oth);
        BigDecimal margin = AgProfitabilityRules.margin(rev, direct);
        return new Totals(money(rev), money(rec), money(lab), money(eq), money(fu), money(oth), money(direct), margin, AgProfitabilityRules.marginPercent(rev, margin));
    }

    private static Subtotal subtotal(List<UnitProfit> rows, String state) {
        int n = 0; BigDecimal rev = BigDecimal.ZERO, direct = BigDecimal.ZERO;
        for (UnitProfit r : rows) if (state.equals(r.state())) { n++; rev = rev.add(r.revenue()); direct = direct.add(r.directCost()); }
        BigDecimal margin = AgProfitabilityRules.margin(rev, direct);
        return new Subtotal(n, money(rev), money(direct), margin, AgProfitabilityRules.marginPercent(rev, margin));
    }
}
