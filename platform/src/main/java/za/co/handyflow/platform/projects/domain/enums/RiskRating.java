package za.co.handyflow.platform.projects.domain.enums;

public enum RiskRating {
    GREEN,   // score < 9
    AMBER,   // score 9–14
    RED;     // score ≥ 15

    public static RiskRating fromScore(int score) {
        if (score >= 15) return RED;
        if (score >= 9)  return AMBER;
        return GREEN;
    }

    public boolean isEscalated() { return this == RED; }
}
