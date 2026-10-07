// security/application/internal/GuardReadinessCalculator.java
package za.co.handyflow.platform.security.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deployment readiness for a guard, from what is on file. Pure: no database, no clock of its own.
 *
 * Required checks (default set): a PSiRA number with an unexpired card, an ID copy in the guard file, and a
 * criminal record check, reference check and drug test that each passed, have an evidence file attached and
 * are in date. Other screening types are shown, and only a failed or expired one blocks readiness.
 * Percent is the share of required checks met. A reviewer's sign-off is shown but does not decide
 * readiness unless it is NOT_CLEARED. The required screening types and documents come from the tenant's
 * Requirements (the defaults above until the tenant changes them); the PSiRA registration is always required.
 */
public final class GuardReadinessCalculator {

    public static final int DUE_SOON_DAYS = 30;

    /** Screening types that count towards the percentage. */
    public static final List<String> REQUIRED_SCREENING = List.of("CRIMINAL_RECORD_CHECK", "REFERENCE_CHECK", "DRUG_TEST");
    /** Guard-file documents required by default. */
    public static final List<String> DEFAULT_DOCUMENTS = List.of("ID_COPY");
    /** Screening types always shown in the matrix, required or not. */
    public static final List<String> SHOWN_SCREENING = List.of(
            "ID_VERIFICATION", "CRIMINAL_RECORD_CHECK", "REFERENCE_CHECK", "DRUG_TEST", "POLYGRAPH", "PSYCHOMETRIC");

    public enum State { MET, EXPIRING, PENDING, INCOMPLETE, UNVERIFIED, EXPIRED, FAILED, MISSING }

    public record ScreeningFacts(String type, String result, LocalDate conductedAt, LocalDate nextDueAt,
                                 Instant createdAt, int evidenceCount, String decision) {}

    /** A competency the guard holds (first aid, firearm competency...). `required` is set by the supervisor per guard. */
    public record CompetencyFacts(String id, String type, String title, boolean required, LocalDate expiry,
                                  int evidenceCount, boolean verified) {}

    /** What a tenant requires for readiness. Screening types and guard-file document categories, by enum name. */
    public record Requirements(Set<String> screening, Set<String> documents) {
        public static Requirements defaults() {
            return new Requirements(new java.util.LinkedHashSet<>(REQUIRED_SCREENING), new java.util.LinkedHashSet<>(DEFAULT_DOCUMENTS));
        }
    }

    public record Input(LocalDate today, String psiraNumber, LocalDate psiraExpiry,
                        List<ScreeningFacts> screenings, Set<String> documentCategories,
                        List<CompetencyFacts> competencies, Requirements requirements) {
        public Input(LocalDate today, String psiraNumber, LocalDate psiraExpiry,
                     List<ScreeningFacts> screenings, Set<String> documentCategories,
                     List<CompetencyFacts> competencies) {
            this(today, psiraNumber, psiraExpiry, screenings, documentCategories, competencies, Requirements.defaults());
        }
        public Input(LocalDate today, String psiraNumber, LocalDate psiraExpiry,
                     List<ScreeningFacts> screenings, Set<String> documentCategories) {
            this(today, psiraNumber, psiraExpiry, screenings, documentCategories, List.of(), Requirements.defaults());
        }
    }

    public record Item(String key, String label, boolean required, State state, String detail,
                       LocalDate validUntil, int evidenceCount, boolean met) {}

    public record Result(int percent, boolean ready, List<Item> items, List<String> reasons) {}

    private GuardReadinessCalculator() {}

    public static Result calculate(Input in) {
        List<Item> items = new ArrayList<>();
        items.add(psira(in));
        Requirements req = in.requirements() == null ? Requirements.defaults() : in.requirements();
        // ID copy first (as it always was), then any other required document, in a fixed order.
        List<String> docs = new ArrayList<>();
        if (req.documents().contains("ID_COPY")) docs.add("ID_COPY");
        req.documents().stream().filter(d -> !d.equals("ID_COPY")).sorted().forEach(docs::add);
        for (String d : docs) items.add(document(d, in.documentCategories().contains(d)));

        Map<String, ScreeningFacts> latest = in.screenings().stream().collect(Collectors.toMap(
                ScreeningFacts::type, f -> f,
                (a, b) -> a.createdAt().isAfter(b.createdAt()) ? a : b));
        List<String> types = new ArrayList<>(SHOWN_SCREENING);
        for (String t : req.screening()) if (!types.contains(t)) types.add(t);
        for (String t : latest.keySet()) if (!types.contains(t)) types.add(t);
        for (String t : types) {
            items.add(screening(t, latest.get(t), req.screening().contains(t), in.today()));
        }

        for (CompetencyFacts c : in.competencies()) items.add(competency(c, in.today()));

        List<Item> required = items.stream().filter(Item::required).toList();
        long met = required.stream().filter(Item::met).count();
        int percent = required.isEmpty() ? 0 : (int) Math.round(met * 100.0 / required.size());
        boolean optionalBlocked = items.stream().anyMatch(i -> !i.required() && !isCompetency(i)
                && (i.state() == State.FAILED || i.state() == State.EXPIRED));
        boolean ready = met == required.size() && !optionalBlocked;

        List<Item> problems = items.stream()
                .filter(i -> (i.required() && !i.met()) || (!i.required() && !isCompetency(i) && (i.state() == State.FAILED || i.state() == State.EXPIRED)))
                .sorted(Comparator.comparingInt(i -> severity(i.state())))
                .toList();
        List<String> reasons = problems.stream().map(i -> i.label() + ": " + i.detail()).toList();
        return new Result(percent, ready, List.copyOf(items), reasons);
    }

    private static int severity(State s) {
        return switch (s) { case FAILED -> 0; case EXPIRED -> 1; case MISSING -> 2; case INCOMPLETE -> 3; case UNVERIFIED -> 4; case PENDING -> 5; default -> 6; };
    }

    public static final String COMPETENCY_PREFIX = "COMPETENCY:";

    private static boolean isCompetency(Item i) { return i.key().startsWith(COMPETENCY_PREFIX); }

    /**
     * A competency is met when it is in date (or has no expiry), has at least one evidence file and has been
     * verified by a named person. Only competencies marked required count towards the percentage, and an
     * expired optional competency is shown but does not block readiness.
     */
    public static Item competency(CompetencyFacts c, LocalDate today) {
        String label = c.title() != null && !c.title().isBlank() ? c.title() : labelOfCompetency(c.type());
        String key = COMPETENCY_PREFIX + c.id();
        long days = c.expiry() == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, c.expiry());
        if (c.expiry() != null && days < 0)
            return new Item(key, label, c.required(), State.EXPIRED, "expired " + plural(-days) + " ago", c.expiry(), c.evidenceCount(), false);
        if (c.evidenceCount() == 0)
            return new Item(key, label, c.required(), State.INCOMPLETE, "no certificate attached", c.expiry(), 0, false);
        if (!c.verified())
            return new Item(key, label, c.required(), State.UNVERIFIED, "awaiting verification", c.expiry(), c.evidenceCount(), false);
        if (c.expiry() != null && days <= DUE_SOON_DAYS)
            return new Item(key, label, c.required(), State.EXPIRING, days == 0 ? "expires today" : "expires in " + plural(days), c.expiry(), c.evidenceCount(), true);
        return new Item(key, label, c.required(), State.MET, c.expiry() == null ? "verified, no expiry" : "verified", c.expiry(), c.evidenceCount(), true);
    }

    public static String labelOfCompetency(String type) {
        return switch (type) {
            case "FIREARM_COMPETENCY" -> "Firearm competency";
            case "FIRST_AID" -> "First aid";
            case "FIREFIGHTING" -> "Firefighting";
            case "DRIVER" -> "Driver";
            case "CLOSE_PROTECTION" -> "Close protection";
            case "VIP_PROTECTION" -> "VIP protection";
            case "CONTROL_ROOM" -> "Control room";
            case "CCTV" -> "CCTV";
            case "ACCESS_CONTROL" -> "Access control";
            case "CANINE" -> "Canine handling";
            case "MINING_SECURITY" -> "Mining security";
            case "TACTICAL_RESPONSE" -> "Tactical response";
            default -> "Other competency";
        };
    }

    private static Item psira(Input in) {
        String label = "PSiRA registration";
        if (in.psiraNumber() == null || in.psiraNumber().isBlank())
            return new Item("PSIRA", label, true, State.MISSING, "no PSiRA number on file", null, 0, false);
        if (in.psiraExpiry() == null)
            return new Item("PSIRA", label, true, State.INCOMPLETE, "no expiry date on file", null, 0, false);
        long days = ChronoUnit.DAYS.between(in.today(), in.psiraExpiry());
        if (days < 0) return new Item("PSIRA", label, true, State.EXPIRED, "expired " + plural(-days) + " ago", in.psiraExpiry(), 0, false);
        if (days <= DUE_SOON_DAYS)
            return new Item("PSIRA", label, true, State.EXPIRING, days == 0 ? "expires today" : "expires in " + plural(days), in.psiraExpiry(), 0, true);
        return new Item("PSIRA", label, true, State.MET, "valid", in.psiraExpiry(), 0, true);
    }

    /** A required guard-file document. The ID copy keeps its original key so existing links and tests are unchanged. */
    private static Item document(String category, boolean has) {
        String key = category.equals("ID_COPY") ? "ID_COPY" : DOCUMENT_PREFIX + category;
        return new Item(key, labelOfDocument(category) + " on file", true, has ? State.MET : State.MISSING,
                has ? "on file" : "not in the guard file", null, 0, has);
    }

    public static final String DOCUMENT_PREFIX = "DOCUMENT:";

    public static String labelOfDocument(String category) {
        return switch (category) {
            case "ID_COPY" -> "ID copy";
            case "PSIRA_CERTIFICATE" -> "PSiRA certificate";
            case "POPIA_CONSENT" -> "POPIA consent";
            case "FIREARM_LICENSE" -> "Firearm licence";
            case "DRIVERS_LICENSE" -> "Driver's licence";
            default -> { String t = category.toLowerCase().replace('_', ' '); yield Character.toUpperCase(t.charAt(0)) + t.substring(1); }
        };
    }

    private static Item screening(String type, ScreeningFacts f, boolean required, LocalDate today) {
        String label = labelOf(type);
        if (f == null) return new Item(type, label, required, State.MISSING, "not on file", null, 0, false);
        int ev = f.evidenceCount();
        if ("NOT_CLEARED".equals(f.decision()))
            return new Item(type, label, required, State.FAILED, "not cleared by the reviewer", f.nextDueAt(), ev, false);
        switch (f.result()) {
            case "FAIL": return new Item(type, label, required, State.FAILED, "failed", f.nextDueAt(), ev, false);
            case "PENDING": return new Item(type, label, required, State.PENDING, "result still pending", f.nextDueAt(), ev, false);
            case "INCONCLUSIVE": return new Item(type, label, required, State.INCOMPLETE, "inconclusive, needs repeating", f.nextDueAt(), ev, false);
            default: break;
        }
        if (f.nextDueAt() != null) {
            long days = ChronoUnit.DAYS.between(today, f.nextDueAt());
            if (days < 0) return new Item(type, label, required, State.EXPIRED, "expired " + plural(-days) + " ago", f.nextDueAt(), ev, false);
        }
        if (ev == 0) return new Item(type, label, required, State.INCOMPLETE, "passed but no evidence file attached", f.nextDueAt(), 0, false);
        if (f.nextDueAt() != null) {
            long days = ChronoUnit.DAYS.between(today, f.nextDueAt());
            if (days <= DUE_SOON_DAYS)
                return new Item(type, label, required, State.EXPIRING, days == 0 ? "renewal due today" : "renewal due in " + plural(days), f.nextDueAt(), ev, true);
        }
        return new Item(type, label, required, State.MET, "passed with evidence", f.nextDueAt(), ev, true);
    }

    private static String plural(long days) { return days + (days == 1 ? " day" : " days"); }

    static String labelOf(String type) {
        return switch (type) {
            case "CRIMINAL_RECORD_CHECK" -> "Criminal record check";
            case "REFERENCE_CHECK" -> "Reference check";
            case "DRUG_TEST" -> "Drug test";
            case "POLYGRAPH" -> "Polygraph";
            case "PSYCHOMETRIC" -> "Psychometric";
            case "CREDIT_CHECK" -> "Credit check";
            case "ID_VERIFICATION" -> "ID verification";
            case "QUALIFICATION_VERIFICATION" -> "Qualification verification";
            default -> "Other screening";
        };
    }
}
