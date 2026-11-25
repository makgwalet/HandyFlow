package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Practice-set rules for who must approve a quote. They are added to the discount limits, never instead of them: the result is the strictest of the two.
 * A rule applies when every condition it names holds. Fees are professional fees after discount and before VAT.
 */
public final class ApprovalRules {

    private ApprovalRules() {}

    public static final String PROPOSED = "PROPOSED", ACTIVE = "ACTIVE", RETIRED = "RETIRED";
    public static final Set<String> APPROVERS = Set.of("COMPLIANCE_MANAGER", "PRACTICE_OWNER", "PRACTICE_OWNER_WITH_EXCEPTION");
    public static final Set<String> RISKS = Set.of("LOW", "MEDIUM", "HIGH");

    public record Rule(String name, BigDecimal minFees, String serviceCode, String riskRating, UUID clientId, String requiredApprover) {}
    public record Outcome(String requiredApprover, String basis) {}

    public static boolean matches(Rule r, BigDecimal fees, Set<String> serviceCodes, String clientRisk, UUID clientId) {
        boolean any = false;
        if (r.minFees() != null) { any = true; if (fees == null || fees.compareTo(r.minFees()) < 0) return false; }
        if (r.serviceCode() != null) { any = true; if (serviceCodes == null || !serviceCodes.contains(r.serviceCode())) return false; }
        if (r.riskRating() != null) { any = true; if (!r.riskRating().equals(clientRisk)) return false; }
        if (r.clientId() != null) { any = true; if (!r.clientId().equals(clientId)) return false; }
        return any;
    }

    /** The strictest of the discount-based approver and every rule that applies, and a plain note of which rules applied. */
    public static Outcome apply(String discountApprover, List<Rule> rules, BigDecimal fees, Set<String> serviceCodes, String clientRisk, UUID clientId) {
        String top = discountApprover;
        List<String> names = new ArrayList<>();
        for (Rule r : rules) {
            if (!matches(r, fees, serviceCodes, clientRisk, clientId)) continue;
            names.add(r.name() + " (" + r.requiredApprover().toLowerCase().replace('_', ' ') + ")");
            if (QuoteRules.APPROVER_ORDER.indexOf(r.requiredApprover()) > QuoteRules.APPROVER_ORDER.indexOf(top)) top = r.requiredApprover();
        }
        return new Outcome(top, names.isEmpty() ? null : "Approval rules that applied: " + String.join("; ", names));
    }

    /** What is wrong with a proposed rule, or null. */
    public static String problem(String name, BigDecimal minFees, String serviceCode, String risk, UUID clientId, String approver, String reason) {
        if (name == null || name.trim().length() < 3) return "Give the rule a name of at least 3 characters";
        if (name.trim().length() > 120) return "The name is longer than 120 characters";
        if (minFees == null && (serviceCode == null || serviceCode.isBlank()) && risk == null && clientId == null) return "Say when the rule applies: a fee amount, a service, a risk rating or a client";
        if (minFees != null && minFees.signum() <= 0) return "The fee amount must be more than zero";
        if (risk != null && !RISKS.contains(risk)) return "The risk rating must be low, medium or high";
        if (approver == null || !APPROVERS.contains(approver)) return "Choose who must approve";
        if (reason == null || reason.trim().length() < 10) return "Give the reason for the rule, at least 10 characters";
        return null;
    }

    public static String note(String raw) {
        if (raw == null || raw.trim().length() < 10) throw new IllegalArgumentException("Give a reason of at least 10 characters");
        return raw.trim();
    }
}
