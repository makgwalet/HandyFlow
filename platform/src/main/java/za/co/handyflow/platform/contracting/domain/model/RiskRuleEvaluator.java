package za.co.handyflow.platform.contracting.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

/**
 * Applies one rule to the facts of one contract. Deterministic and side-effect free: it checks what is recorded or written, and
 * says nothing about whether the contract is lawful. A rule fails (returns a message) when its condition for concern is met.
 */
public final class RiskRuleEvaluator {

    private RiskRuleEvaluator() {}

    /** The facts a rule can look at. {@code clauseCategories} are the categories of library clauses in the contract that are still current. */
    public record Facts(String contractType, String bodyText, BigDecimal value, LocalDate startDate, LocalDate endDate,
                        boolean autoRenew, int renewalNoticeDays, Set<String> clauseCategories) {}

    /** Returns the rule's failure message when the contract trips the rule, or empty when it passes. */
    public static Optional<String> evaluate(ContractRiskRule rule, Facts f) {
        boolean failed = switch (rule.getRuleType()) {
            case "REQUIRES_CLAUSE_CATEGORY" -> !f.clauseCategories().contains(rule.getParamText());
            case "REQUIRES_PHRASE" -> !plain(f.bodyText()).contains(plain(rule.getParamText()));
            case "FORBIDS_PHRASE" -> plain(f.bodyText()).contains(plain(rule.getParamText()));
            case "REQUIRES_END_DATE" -> f.endDate() == null;
            case "REQUIRES_VALUE" -> f.value() == null;
            case "VALUE_ABOVE" -> f.value() != null && f.value().compareTo(rule.getParamNumber()) > 0;
            case "TERM_LONGER_THAN_MONTHS" -> f.startDate() != null && f.endDate() != null
                    && ChronoUnit.MONTHS.between(f.startDate(), f.endDate()) > rule.getParamNumber().longValue();
            case "AUTO_RENEW_NOTICE_BELOW_DAYS" -> f.autoRenew() && f.renewalNoticeDays() < rule.getParamNumber().longValue();
            default -> throw new IllegalStateException("Unknown rule type: " + rule.getRuleType());
        };
        return failed ? Optional.of(rule.getMessage()) : Optional.empty();
    }

    /** Text without tags, in lower case, with runs of whitespace collapsed, so a phrase matches across formatting. */
    static String plain(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
