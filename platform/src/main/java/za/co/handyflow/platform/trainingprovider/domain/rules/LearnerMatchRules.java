package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds records that may be the same person, and checks the steps of linking and merging them. This only points things out. It never
 * decides that two records are one person: a match is a reason for a person to look, and a merge is made by a person and approved by a second.
 */
public final class LearnerMatchRules {

    public static final String STRONG = "STRONG";
    public static final String POSSIBLE = "POSSIBLE";
    public static final String NONE = "NONE";
    public static final int MIN_REASON = 10;

    private LearnerMatchRules() {
    }

    /** What is known about one record. A delegate has one name field, so the learner's names are joined before comparing. */
    public record Person(UUID id, String name, String idType, String idNumber, LocalDate dateOfBirth, String email, String phone) {
    }

    /** strength is STRONG, POSSIBLE or NONE. reasons say what agreed. conflicts say what disagreed. */
    public record Match(String strength, List<String> reasons, List<String> conflicts) {
        public boolean found() {
            return !NONE.equals(strength);
        }
    }

    public static String nameKey(String name) {
        if (name == null) {
            return "";
        }
        String[] parts = name.toLowerCase().replaceAll("[^\\p{L}\\s]", " ").trim().split("\\s+");
        return Arrays.stream(parts).filter(s -> !s.isEmpty()).sorted().reduce((a, b) -> a + " " + b).orElse("");
    }

    public static String idKey(String idNumber) {
        return idNumber == null ? "" : idNumber.toLowerCase().replaceAll("[\\s-]", "");
    }

    public static String emailKey(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** The last nine digits, so +27 82 123 4567, 0821234567 and 0027821234567 agree. */
    public static String phoneKey(String phone) {
        if (phone == null) {
            return "";
        }
        String d = phone.replaceAll("\\D", "");
        return d.length() < 9 ? "" : d.substring(d.length() - 9);
    }

    public static Match compare(Person a, Person b) {
        List<String> reasons = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        boolean id = !idKey(a.idNumber()).isEmpty() && idKey(a.idNumber()).equals(idKey(b.idNumber()));
        boolean name = !nameKey(a.name()).isEmpty() && nameKey(a.name()).equals(nameKey(b.name()));
        boolean dob = a.dateOfBirth() != null && a.dateOfBirth().equals(b.dateOfBirth());
        boolean email = !emailKey(a.email()).isEmpty() && emailKey(a.email()).equals(emailKey(b.email()));
        boolean phone = !phoneKey(a.phone()).isEmpty() && phoneKey(a.phone()).equals(phoneKey(b.phone()));
        if (id) {
            reasons.add("ID_NUMBER");
        }
        if (name) {
            reasons.add("NAME");
        }
        if (dob) {
            reasons.add("DATE_OF_BIRTH");
        }
        if (email) {
            reasons.add("EMAIL");
        }
        if (phone) {
            reasons.add("PHONE");
        }
        if (!idKey(a.idNumber()).isEmpty() && !idKey(b.idNumber()).isEmpty() && !id) {
            conflicts.add("The identity numbers differ");
        }
        if (a.dateOfBirth() != null && b.dateOfBirth() != null && !dob) {
            conflicts.add("The dates of birth differ");
        }
        boolean strong = id || (name && (dob || email || phone)) || (email && phone);
        String strength;
        if (reasons.isEmpty() || (reasons.size() == 1 && dob)) {
            strength = NONE;
        } else if (strong && conflicts.isEmpty()) {
            strength = STRONG;
        } else {
            strength = POSSIBLE;
        }
        return new Match(strength, reasons, conflicts);
    }

    public record Pair(Person first, Person second, Match match) {
    }

    /**
     * Every pair that shares an identity number, an email, a phone number or a name, compared in full. Records are grouped first, so
     * a long list does not mean comparing every record with every other. Strong pairs come first.
     */
    public static List<Pair> candidatePairs(List<Person> people) {
        Map<String, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < people.size(); i++) {
            Person p = people.get(i);
            for (String k : new String[]{"i:" + idKey(p.idNumber()), "e:" + emailKey(p.email()), "p:" + phoneKey(p.phone()), "n:" + nameKey(p.name())}) {
                if (k.length() > 2) {
                    buckets.computeIfAbsent(k, x -> new ArrayList<>()).add(i);
                }
            }
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<Pair> out = new ArrayList<>();
        for (List<Integer> group : buckets.values()) {
            for (int x = 0; x < group.size(); x++) {
                for (int y = x + 1; y < group.size(); y++) {
                    int a = Math.min(group.get(x), group.get(y));
                    int b = Math.max(group.get(x), group.get(y));
                    if (seen.add((long) a * people.size() + b)) {
                        Match m = compare(people.get(a), people.get(b));
                        if (m.found()) {
                            out.add(new Pair(people.get(a), people.get(b), m));
                        }
                    }
                }
            }
        }
        out.sort((l, r) -> {
            int c = Integer.compare(STRONG.equals(l.match().strength()) ? 0 : 1, STRONG.equals(r.match().strength()) ? 0 : 1);
            return c != 0 ? c : nameKey(l.first().name()).compareTo(nameKey(r.first().name()));
        });
        return out;
    }

    /** Why a delegate cannot be linked to this learner without a written reason, or null. A differing identity number is the one hard sign of two people. */
    public static String linkConflict(String learnerIdNumber, String delegateIdNumber) {
        if (!idKey(learnerIdNumber).isEmpty() && !idKey(delegateIdNumber).isEmpty() && !idKey(learnerIdNumber).equals(idKey(delegateIdNumber))) {
            return "The learner's and the delegate's identity numbers differ. Say why they are the same person, or choose another delegate";
        }
        return null;
    }

    public static boolean reasonOk(String reason) {
        return reason != null && reason.trim().length() >= MIN_REASON;
    }

    /** Why this pair cannot be proposed for a merge, or null. */
    public static String proposalProblem(UUID survivor, UUID merged, String survivorStatus, String mergedStatus, UUID mergedIntoOfSurvivor,
                                         UUID mergedIntoOfMerged, List<String> conflicts, boolean conflictsAccepted, String reason) {
        if (survivor == null || merged == null) {
            return "Choose both learners";
        }
        if (survivor.equals(merged)) {
            return "A learner cannot be merged into itself";
        }
        if (!"ACTIVE".equals(survivorStatus) || mergedIntoOfSurvivor != null) {
            return "The learner to keep must be live";
        }
        if (!"ACTIVE".equals(mergedStatus) || mergedIntoOfMerged != null) {
            return "The learner to merge must be live. An archived or already merged learner cannot be merged";
        }
        if (conflicts != null && !conflicts.isEmpty() && !conflictsAccepted) {
            return "The records disagree (" + String.join("; ", conflicts) + "). Confirm that you have checked this";
        }
        if (!reasonOk(reason)) {
            return "Say why these are the same person, in at least " + MIN_REASON + " characters";
        }
        return null;
    }

    /** Why this person cannot decide the merge, or null. A second person decides, or the same person says why alone. */
    public static String decisionProblem(String status, UUID proposer, UUID decider, boolean confirmAlone, String singleReason, String decisionReason) {
        if (!"PROPOSED".equals(status)) {
            return "This merge has already been decided";
        }
        if (!reasonOk(decisionReason)) {
            return "Say why, in at least " + MIN_REASON + " characters";
        }
        if (decider != null && decider.equals(proposer)) {
            if (!confirmAlone) {
                return "A second person needs to confirm a merge you proposed, or say why you are confirming it alone";
            }
            if (!reasonOk(singleReason)) {
                return "Say why no second person is confirming, in at least " + MIN_REASON + " characters";
            }
        }
        return null;
    }

    /** Programmes where both learners have a live enrolment or open application. The two could not sit on one learner. */
    public static List<UUID> clashes(List<UUID> survivorProgrammes, List<UUID> mergedProgrammes) {
        List<UUID> out = new ArrayList<>();
        for (UUID p : mergedProgrammes) {
            if (survivorProgrammes.contains(p) && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** A readable copy of a learner as it stood, kept with the merge. */
    public static String snapshot(String learnerNo, String name, String idType, String idNumber, LocalDate dob, String email, String phone,
                                  UUID clientId, UUID delegateId) {
        return "Learner " + learnerNo + "; " + name + "; " + idType + " " + (idNumber == null ? "-" : idNumber) + "; born " + (dob == null ? "-" : dob)
                + "; " + (email == null ? "-" : email) + "; " + (phone == null ? "-" : phone)
                + "; client " + (clientId == null ? "-" : clientId) + "; delegate " + (delegateId == null ? "-" : delegateId);
    }
}
