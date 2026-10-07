package za.co.handyflow.platform.clinic.domain.question;

/** Patient and visit facts a rule can look at besides the answers. Any field may be null (unknown). */
public record RuleContext(Integer ageMonths, String sexAtBirth, String pregnancyStatus, String visitType) {
    public static RuleContext empty() { return new RuleContext(null, null, null, null); }

    Object get(String name) {
        return switch (name) {
            case "ageMonths"       -> ageMonths;
            case "sexAtBirth"      -> sexAtBirth;
            case "pregnancyStatus" -> pregnancyStatus;
            case "visitType"       -> visitType;
            default -> throw new IllegalArgumentException("Unknown context field '" + name + "'.");
        };
    }
}
