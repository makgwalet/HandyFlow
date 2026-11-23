package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * The rules of a breeding record that used to be missing: any button could be pressed on any record (a failed record could
 * be marked born), a stillbirth was stored as a birth, dates were not compared, and the dam and sire were not checked.
 * <p>
 * Life of a record: PENDING (mated) -> CONFIRMED_PREGNANT -> BIRTHED or STILLBORN. A birth may be recorded straight from
 * PENDING, because many farms never confirm. NOT_PREGNANT, FAILED, ABORTED, BIRTHED and STILLBORN are final.
 * BIRTHED means at least one calf/lamb/kid was born alive; STILLBORN means none were.
 */
public final class AgBreedingRules {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED_PREGNANT = "CONFIRMED_PREGNANT";
    public static final String NOT_PREGNANT = "NOT_PREGNANT";
    public static final String BIRTHED = "BIRTHED";
    public static final String STILLBORN = "STILLBORN";
    public static final String ABORTED = "ABORTED";
    public static final String FAILED = "FAILED";

    public static final Set<String> BREEDING_TYPES = Set.of("NATURAL", "AI", "EMBRYO_TRANSFER");
    static final int MAX_BORN = 100;

    private static final Map<String, Set<String>> NEXT = Map.of(
            PENDING, Set.of(CONFIRMED_PREGNANT, NOT_PREGNANT, BIRTHED, STILLBORN, ABORTED, FAILED),
            CONFIRMED_PREGNANT, Set.of(BIRTHED, STILLBORN, ABORTED, FAILED));

    private AgBreedingRules() {}

    /** The only outcomes that can follow this one. Final outcomes have none. */
    public static Set<String> allowedNext(String from) {
        return from == null ? Set.of() : NEXT.getOrDefault(from, Set.of());
    }

    public static void requireTransition(String from, String to) {
        if (!allowedNext(from).contains(to))
            throw new IllegalStateException("A breeding record that is " + label(from) + " cannot be changed to " + label(to));
    }

    /** "AI" is what is stored; the long spelling the web screen used to send is accepted and shortened. */
    public static String normaliseType(String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("breedingType is required");
        String t = type.trim().toUpperCase();
        if (t.equals("ARTIFICIAL_INSEMINATION")) t = "AI";
        if (!BREEDING_TYPES.contains(t))
            throw new IllegalArgumentException("breedingType must be one of NATURAL, AI, EMBRYO_TRANSFER");
        return t;
    }

    public static void requireMatingDates(LocalDate mating, LocalDate expectedDue, LocalDate today) {
        if (mating == null) throw new IllegalArgumentException("matingDate is required");
        if (mating.isAfter(today)) throw new IllegalArgumentException("The mating date cannot be in the future");
        if (expectedDue != null && expectedDue.isBefore(mating))
            throw new IllegalArgumentException("The expected due date cannot be before the mating date");
    }

    public static void requireBirthDate(LocalDate mating, LocalDate birth, LocalDate today) {
        if (birth == null) throw new IllegalArgumentException("actualBirthDate is required");
        if (birth.isAfter(today)) throw new IllegalArgumentException("The birth date cannot be in the future");
        if (birth.isBefore(mating)) throw new IllegalArgumentException("The birth date cannot be before the mating date");
    }

    /** Missing counts are zero. Something must have been born. */
    public static int[] requireBirthCounts(Integer live, Integer stillborn) {
        int l = live == null ? 0 : live;
        int s = stillborn == null ? 0 : stillborn;
        if (l < 0 || s < 0) throw new IllegalArgumentException("Counts cannot be negative");
        if (l > MAX_BORN || s > MAX_BORN) throw new IllegalArgumentException("A count of more than " + MAX_BORN + " is not credible");
        if (l + s == 0) throw new IllegalArgumentException("Record at least one animal born alive or stillborn");
        return new int[]{l, s};
    }

    public static String outcomeForBirth(int live) {
        return live > 0 ? BIRTHED : STILLBORN;
    }

    /** The mother must be a female that is still on the farm. */
    public static void requireDam(String sex, String status) {
        if (!"FEMALE".equals(sex)) throw new IllegalArgumentException("Only a female can be bred");
        if (!"ACTIVE".equals(status)) throw new IllegalArgumentException("This animal is " + label(status) + " and cannot be bred");
    }

    /** The sire must be an uncastrated male that is still on the farm. */
    public static void requireSire(String sex, String status) {
        if (!"MALE".equals(sex)) throw new IllegalArgumentException("The sire must be a male that is not castrated");
        if (!"ACTIVE".equals(status)) throw new IllegalArgumentException("The sire is " + label(status) + " and cannot be used");
    }

    private static String label(String s) {
        return s == null ? "unknown" : s.toLowerCase().replace('_', ' ');
    }
}
