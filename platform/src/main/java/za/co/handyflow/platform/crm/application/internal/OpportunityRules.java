package za.co.handyflow.platform.crm.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import za.co.handyflow.platform.crm.domain.model.LostReason;
import za.co.handyflow.platform.crm.domain.model.OpportunityStage;

/** The rules for opportunities, kept free of Spring and the database so they can be tested alone. */
final class OpportunityRules {
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private OpportunityRules() {}

    static String cleanName(String raw) {
        String n = raw == null ? "" : raw.strip();
        if (n.isEmpty()) throw new IllegalArgumentException("Give the opportunity a name");
        if (n.length() > 255) throw new IllegalArgumentException("The name can be at most 255 characters");
        return n;
    }

    static BigDecimal cleanAmount(BigDecimal raw) {
        if (raw == null) return BigDecimal.ZERO.setScale(2);
        if (raw.signum() < 0) throw new IllegalArgumentException("The amount cannot be negative");
        if (raw.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("That amount is too large");
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    /** Trimmed note or null. */
    static String cleanNote(String raw, int max, String what) {
        if (raw == null || raw.isBlank()) return null;
        String n = raw.strip();
        if (n.length() > max) throw new IllegalArgumentException(what + " can be at most " + max + " characters");
        return n;
    }

    /** Checks a stage move. Won and lost are final until the opportunity is moved back to an open stage. */
    static void checkMove(OpportunityStage from, OpportunityStage to, LostReason reason, String note) {
        if (from == to) throw new IllegalStateException("It is already " + label(to));
        if (from == OpportunityStage.WON && to == OpportunityStage.LOST) throw new IllegalStateException("Reopen the opportunity before closing it as lost");
        if (from == OpportunityStage.LOST && to == OpportunityStage.WON) throw new IllegalStateException("Reopen the opportunity before closing it as won");
        if (to == OpportunityStage.LOST) {
            if (reason == null) throw new IllegalArgumentException("Choose why the opportunity was lost");
            if (reason == LostReason.OTHER && (note == null || note.isBlank())) throw new IllegalArgumentException("Add a note saying why the opportunity was lost");
        }
    }

    static String label(OpportunityStage s) { return s.name().toLowerCase().replace('_', ' '); }

    /** Amount x chance, for open opportunities only. */
    static BigDecimal weighted(OpportunityStage stage, BigDecimal amount) {
        if (!stage.isOpen() || amount == null) return BigDecimal.ZERO.setScale(2);
        return amount.multiply(BigDecimal.valueOf(stage.chance())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** One opportunity as the summary sees it. */
    record Item(OpportunityStage stage, BigDecimal amount, LocalDate expectedCloseDate) {}

    record StageTotal(OpportunityStage stage, int count, BigDecimal value) {}

    record Summary(int openCount, BigDecimal openValue, BigDecimal weightedValue, int overdueCount, int wonCount, BigDecimal wonValue,
                   int lostCount, List<StageTotal> byStage) {}

    static Summary summarise(List<Item> items, LocalDate today) {
        Map<OpportunityStage, int[]> counts = new EnumMap<>(OpportunityStage.class);
        Map<OpportunityStage, BigDecimal> values = new EnumMap<>(OpportunityStage.class);
        for (OpportunityStage s : OpportunityStage.values()) { counts.put(s, new int[1]); values.put(s, BigDecimal.ZERO.setScale(2)); }
        BigDecimal weighted = BigDecimal.ZERO.setScale(2);
        int overdue = 0;
        for (Item i : items) {
            BigDecimal amt = i.amount() == null ? BigDecimal.ZERO : i.amount();
            counts.get(i.stage())[0]++;
            values.put(i.stage(), values.get(i.stage()).add(amt));
            weighted = weighted.add(weighted(i.stage(), amt));
            if (i.stage().isOpen() && i.expectedCloseDate() != null && i.expectedCloseDate().isBefore(today)) overdue++;
        }
        int open = 0; BigDecimal openValue = BigDecimal.ZERO.setScale(2);
        List<StageTotal> by = new ArrayList<>();
        for (OpportunityStage s : OpportunityStage.values()) {
            by.add(new StageTotal(s, counts.get(s)[0], values.get(s)));
            if (s.isOpen()) { open += counts.get(s)[0]; openValue = openValue.add(values.get(s)); }
        }
        return new Summary(open, openValue, weighted, overdue, counts.get(OpportunityStage.WON)[0], values.get(OpportunityStage.WON), counts.get(OpportunityStage.LOST)[0], by);
    }
}
