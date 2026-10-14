package za.co.handyflow.platform.accountant.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure rules for engagement letters: what must be filled in, which status can move where, and the text of the letter.
 * The standard clauses are a starting point and should be read by the practice's own professional body or lawyer.
 */
public final class EngagementLetterRules {
    private EngagementLetterRules() {}

    public static final String DRAFT = "DRAFT", SENT = "SENT", ACCEPTED = "ACCEPTED", DECLINED = "DECLINED", SUPERSEDED = "SUPERSEDED", ENDED = "ENDED";
    public static final Set<String> FEE_BASES = Set.of("FIXED", "RETAINER", "HOURLY", "MIXED");
    public static final Set<String> ACCEPT_METHODS = Set.of("SIGNED_COPY", "EMAIL", "OTHER");

    public record Draft(String title, String scope, String exclusions, String feeBasis, BigDecimal feeAmount, String feeNotes,
                        LocalDate startDate, LocalDate endDate) {}

    public static String draftProblem(Draft d) {
        if (d.title() == null || d.title().isBlank()) return "Give the letter a title.";
        if (d.title().trim().length() > 150) return "The title is too long.";
        if (d.scope() == null || d.scope().isBlank()) return "Say what services are covered.";
        if (d.scope().length() > 6000 || (d.exclusions() != null && d.exclusions().length() > 3000) || (d.feeNotes() != null && d.feeNotes().length() > 2000))
            return "One of the sections is too long.";
        if (!FEE_BASES.contains(d.feeBasis())) return "Choose how the fees are charged.";
        boolean needsAmount = "FIXED".equals(d.feeBasis()) || "RETAINER".equals(d.feeBasis());
        if (needsAmount && (d.feeAmount() == null || d.feeAmount().signum() <= 0)) return "A fixed or retainer fee needs an amount.";
        if (d.feeAmount() != null && d.feeAmount().signum() < 0) return "The fee cannot be negative.";
        if (d.startDate() == null) return "Choose the start date.";
        if (d.endDate() != null && d.endDate().isBefore(d.startDate())) return "The end date is before the start date.";
        return null;
    }

    public static String editProblem(String status) {
        return DRAFT.equals(status) ? null : "Only a draft letter can be changed. Make a new version instead.";
    }

    public static String sendProblem(String status, Draft d) {
        if (!DRAFT.equals(status)) return "Only a draft letter can be sent.";
        return draftProblem(d);
    }

    public static String acceptProblem(String status, String name, String method, LocalDate on, LocalDate today) {
        if (!SENT.equals(status)) return "Only a letter that was sent can be accepted.";
        if (name == null || name.isBlank()) return "Enter the name of the person who accepted for the client.";
        if (!ACCEPT_METHODS.contains(method)) return "Say how it was accepted.";
        if (on == null) return "Enter the date it was accepted.";
        if (today != null && on.isAfter(today)) return "The acceptance date cannot be in the future.";
        return null;
    }

    public static String declineProblem(String status) {
        return SENT.equals(status) ? null : "Only a letter that was sent can be declined.";
    }

    public static String endProblem(String status) {
        return ACCEPTED.equals(status) ? null : "Only an accepted letter can be ended.";
    }

    /** The letter as plain text, frozen when it is sent. */
    public static String render(String practice, String client, int version, Draft d, LocalDate on) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
        StringBuilder s = new StringBuilder();
        s.append(practice).append('\n').append(on.format(f)).append("\n\n");
        s.append("To: ").append(client).append("\n\n");
        s.append("ENGAGEMENT LETTER: ").append(d.title().trim()).append(" (version ").append(version).append(")\n\n");
        s.append("1. Services covered\n").append(d.scope().trim()).append("\n\n");
        s.append("2. Services not covered\n");
        s.append(d.exclusions() == null || d.exclusions().isBlank()
                ? "Anything not listed above, including audit, review or assurance engagements, is not covered unless we agree it in writing.\n\n"
                : d.exclusions().trim() + "\n\n");
        s.append("3. Fees\n").append(feeLine(d)).append('\n');
        if (d.feeNotes() != null && !d.feeNotes().isBlank()) s.append(d.feeNotes().trim()).append('\n');
        s.append("Fees exclude VAT unless stated. Out-of-pocket costs are charged separately.\n\n");
        s.append("4. Period\nThis letter applies from ").append(d.startDate().format(f))
                .append(d.endDate() == null ? " until either of us ends it." : " to " + d.endDate().format(f) + ".").append("\n\n");
        s.append("5. Your responsibilities\n")
                .append("You will give us complete and accurate records and information on time, tell us about changes that affect our work, ")
                .append("and remain responsible for your own tax and legal compliance and for the decisions you take.\n\n");
        s.append("6. Our responsibilities\n")
                .append("We will do the services above with reasonable skill and care and keep your information confidential. ")
                .append("We handle personal information as required by the Protection of Personal Information Act.\n\n");
        s.append("7. Ending this letter\nEither of us may end it with 30 days' written notice. Fees for work done up to that date remain payable.\n\n");
        s.append("Please confirm your acceptance by signing and returning a copy or replying to this letter.\n");
        return s.toString();
    }

    private static String feeLine(Draft d) {
        String amount = d.feeAmount() == null ? "" : "R " + d.feeAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return switch (d.feeBasis()) {
            case "FIXED" -> "A fixed fee of " + amount + " for the services above.";
            case "RETAINER" -> "A retainer of " + amount + " per billing period, as set out in the service schedule.";
            case "HOURLY" -> "Time is charged at our standard hourly rates" + (d.feeAmount() == null ? "." : ", from " + amount + " per hour.");
            default -> "A retainer" + (d.feeAmount() == null ? "" : " of " + amount) + " plus time charged at our standard hourly rates for work outside the scope.";
        };
    }

    public static List<String> statuses() { return List.of(DRAFT, SENT, ACCEPTED, DECLINED, SUPERSEDED, ENDED); }
}
