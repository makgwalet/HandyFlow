package za.co.handyflow.platform.debtcollection.domain.model;

public enum PriorityBand {
    URGENT, HIGH, MEDIUM, LOW;

    public static PriorityBand of(int score) {
        if (score >= 70) return URGENT;
        if (score >= 50) return HIGH;
        if (score >= 30) return MEDIUM;
        return LOW;
    }
}
