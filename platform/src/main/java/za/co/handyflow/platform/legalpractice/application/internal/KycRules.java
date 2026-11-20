package za.co.handyflow.platform.legalpractice.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What client due diligence has to contain, as plain rules with no database. Written from the Law Society's FICA risk-management
 * guidance (identity and address verification for individuals; registration, authorised persons and beneficial owners for companies;
 * trustees, founders and beneficiaries for trusts; every partner for partnerships; politically exposed person and sanctions checks;
 * a risk rating that sets how much is needed and how often it is reviewed). The firm's own risk-management and compliance programme
 * has the final say, so the review periods here are the guidance's example and are meant to be adjusted by the firm.
 */
public final class KycRules {

    private KycRules() { }

    public static final Set<String> SUBJECT_KINDS = Set.of("INDIVIDUAL", "COMPANY", "TRUST", "PARTNERSHIP");
    public static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    public static final Set<String> PEP = Set.of("NONE", "DPEP", "PIP", "FPEP");
    public static final Set<String> ITEM_KINDS = Set.of("IDENTITY", "ADDRESS", "REGISTRATION", "OWNERSHIP_STRUCTURE", "TRUST_DEED", "OTHER");
    public static final Set<String> PARTY_ROLES = Set.of("BENEFICIAL_OWNER", "AUTHORISED_PERSON", "TRUSTEE", "FOUNDER", "BENEFICIARY", "PARTNER");
    public static final Set<String> OWNER_METHODS = Set.of("OWNERSHIP", "CONTROL", "MANAGEMENT");
    public static final int MIN_TEXT = 10;
    /** The FIC's guidance is to treat a holder of this much or more as a beneficial owner. */
    public static final BigDecimal OWNER_THRESHOLD = new BigDecimal("5");

    /** What the assessment says, as the rules need it. */
    public record Assessment(String subjectKind, String geography, String service, String client, String channel, String pep, LocalDate pepCheckedOn,
                             LocalDate sanctionsCheckedOn, String sanctionsResult, String businessNature, String sourceOfFunds, String sourceOfWealth) { }

    public record Party(String role, String name, BigDecimal percent, String method, boolean verified) { }

    public record Item(String kind, String partyName) { }

    /** The overall rating: the highest of the four indicators, and never below HIGH for a politically exposed person. Null until all four are set. */
    public static String riskOf(String geography, String service, String client, String channel, String pep) {
        if (geography == null || service == null || client == null || channel == null) return null;
        String top = "LOW";
        for (String s : new String[]{geography, service, client, channel}) {
            if ("HIGH".equals(s)) top = "HIGH";
            else if ("MEDIUM".equals(s) && !"HIGH".equals(top)) top = "MEDIUM";
        }
        return pep != null && !"NONE".equals(pep) ? "HIGH" : top;
    }

    /** Simplified, standard or enhanced due diligence. */
    public static String levelOf(String risk) {
        return switch (risk == null ? "" : risk) {
            case "LOW" -> "SIMPLIFIED";
            case "HIGH" -> "ENHANCED";
            default -> "STANDARD";
        };
    }

    /** Months until the due diligence must be reviewed: the guidance's example of annually, two-yearly and three-yearly. */
    public static int reviewMonths(String risk) {
        return switch (risk == null ? "" : risk) {
            case "HIGH" -> 12;
            case "MEDIUM" -> 24;
            default -> 36;
        };
    }

    public static boolean needsSenior(String risk, String pep) {
        return "HIGH".equals(risk) || (pep != null && !"NONE".equals(pep));
    }

    /** Everything still missing for the profile to be submitted. Empty means complete. */
    public static List<String> missing(Assessment a, List<Party> parties, List<Item> items) {
        List<String> m = new ArrayList<>();
        if (a.geography() == null || a.service() == null || a.client() == null || a.channel() == null) {
            m.add("Rate all four risk indicators: country, service, client and delivery channel");
        }
        if (a.pep() == null || a.pepCheckedOn() == null) m.add("Record whether the client, an owner or a close family member or associate is a politically exposed person, and when you checked");
        if (a.sanctionsCheckedOn() == null || a.sanctionsResult() == null) m.add("Screen the client and its owners against the targeted financial sanctions list, and record the date and result");
        else if ("MATCH".equals(a.sanctionsResult())) m.add("A sanctions match must be resolved before this can be submitted");
        if (!text(a.businessNature())) m.add("Describe the client's business or the purpose of the relationship");
        String risk = riskOf(a.geography(), a.service(), a.client(), a.channel(), a.pep());
        if (needsSenior(risk, a.pep())) {
            if (!text(a.sourceOfFunds())) m.add("A high-risk or politically exposed client needs the source of funds");
            if (!text(a.sourceOfWealth())) m.add("A high-risk or politically exposed client needs the source of wealth");
        }
        String kind = a.subjectKind();
        if ("INDIVIDUAL".equals(kind)) {
            need(m, items, "IDENTITY", "Verify the client's identity from an official identity document");
            need(m, items, "ADDRESS", "Verify the client's residential address");
        } else {
            need(m, items, "ADDRESS", "Verify the registered or business address");
            if ("COMPANY".equals(kind)) {
                need(m, items, "REGISTRATION", "Verify the company's registration documents");
                need(m, items, "OWNERSHIP_STRUCTURE", "Record the ownership and control structure");
                requireParties(m, parties, "AUTHORISED_PERSON", 1, "Identify and verify at least one person authorised to instruct the firm");
                requireParties(m, parties, "BENEFICIAL_OWNER", 1, "Identify and verify the beneficial owners");
                ownershipEvidence(m, parties);
            } else if ("TRUST".equals(kind)) {
                need(m, items, "TRUST_DEED", "Verify the trust deed and the Master's letters of authority");
                requireParties(m, parties, "TRUSTEE", 1, "Identify and verify the trustees");
                requireParties(m, parties, "FOUNDER", 1, "Identify and verify the founder");
                requireParties(m, parties, "BENEFICIARY", 1, "Identify and verify the beneficiaries");
                requireParties(m, parties, "AUTHORISED_PERSON", 1, "Identify and verify at least one person authorised to instruct the firm");
            } else if ("PARTNERSHIP".equals(kind)) {
                requireParties(m, parties, "PARTNER", 2, "Identify and verify every partner (at least two)");
                requireParties(m, parties, "AUTHORISED_PERSON", 1, "Identify and verify at least one person authorised to instruct the firm");
            }
        }
        for (Party p : parties) {
            if (!p.verified()) m.add("Verify the identity of " + p.name() + " (" + p.role().toLowerCase().replace('_', ' ') + ")");
        }
        return m;
    }

    private static void need(List<String> m, List<Item> items, String kind, String message) {
        if (items.stream().noneMatch(i -> kind.equals(i.kind()))) m.add(message);
    }

    private static void requireParties(List<String> m, List<Party> parties, String role, int atLeast, String message) {
        if (parties.stream().filter(p -> role.equals(p.role())).count() < atLeast) m.add(message);
    }

    /** A beneficial owner is found by ownership first; only when nobody holds the threshold may control be used, and the way it was found is kept. */
    private static void ownershipEvidence(List<String> m, List<Party> parties) {
        List<Party> owners = parties.stream().filter(p -> "BENEFICIAL_OWNER".equals(p.role())).toList();
        if (owners.isEmpty()) return;
        boolean holder = owners.stream().anyMatch(p -> "OWNERSHIP".equals(p.method()) && p.percent() != null && p.percent().compareTo(OWNER_THRESHOLD) >= 0);
        if (!holder && owners.stream().allMatch(p -> "OWNERSHIP".equals(p.method()))) {
            m.add("No owner holds " + OWNER_THRESHOLD.toPlainString() + "% or more. Say how the owners were identified (control, or senior management as a last resort)");
        }
        for (Party p : owners) {
            if ("OWNERSHIP".equals(p.method()) && p.percent() == null) m.add("Give the ownership percentage for " + p.name());
        }
    }

    private static boolean text(String s) { return s != null && s.trim().length() >= MIN_TEXT; }

    /** Days before the review date from which a client is shown as due for review. */
    public static final int DUE_SOON_DAYS = 60;

    /**
     * Where a client stands, for the overview. A current verification shows as VERIFIED (or DUE_SOON close to the review date) whether or not
     * a review is already open; otherwise an open cycle shows as DRAFT or SUBMITTED; otherwise a lapsed verification shows as EXPIRED.
     */
    public static String overviewStatus(LocalDate verifiedUntil, String openStatus, LocalDate today) {
        if (verifiedUntil != null && !verifiedUntil.isBefore(today)) {
            return verifiedUntil.minusDays(DUE_SOON_DAYS).isAfter(today) ? "VERIFIED" : "DUE_SOON";
        }
        if (openStatus != null) return openStatus;
        return verifiedUntil != null ? "EXPIRED" : "NOT_STARTED";
    }
}
