package za.co.handyflow.platform.supplychain.application.internal;

/**
 * Pure scorecard maths. Weights: on-time delivery 35, fill rate 20, quality (accepted vs delivered) 25,
 * invoice accuracy 20. A component with no data is dropped and the remaining weights re-normalised, so
 * a supplier is never marked down for something that hasn't happened yet. No data at all -> no score.
 * Bands: A >= 90, B >= 75, C >= 60, D below.
 */
public final class SupplierScoreRules {

    private SupplierScoreRules() {}

    public record Metrics(Double onTimePct, Double fillRatePct, Double qualityPct, Double invoiceAccuracyPct) {}
    public record Score(Integer score, String rating) {}

    public static Score score(Metrics m) {
        double[] weights = {35, 20, 25, 20};
        Double[] values = {m.onTimePct(), m.fillRatePct(), m.qualityPct(), m.invoiceAccuracyPct()};
        double sum = 0, wsum = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) continue;
            sum += clamp(values[i]) * weights[i];
            wsum += weights[i];
        }
        if (wsum == 0) return new Score(null, null);
        int score = (int) Math.round(sum / wsum);
        return new Score(score, score >= 90 ? "A" : score >= 75 ? "B" : score >= 60 ? "C" : "D");
    }

    /** part/total as a percentage, or null when there is nothing to divide by. */
    public static Double pct(double part, double total) {
        return total <= 0 ? null : clamp(part / total * 100.0);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
