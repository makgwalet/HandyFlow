package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a recorded birth turns into. Until now a birth only set a count on the breeding record; no animal was created.
 * <ul>
 *   <li>An <b>individually tracked</b> dam: one animal per live offspring, sex entered for each. The tag is whatever the farmer
 *       typed, otherwise a temporary one, {@code <dam tag>-<yy>-<nn>} (for example {@code 1234-26-01}), which the farmer replaces
 *       later with the permanent tag.</li>
 *   <li>A <b>group</b>: the group's head count goes up by the number born alive. No tags.</li>
 *   <li>Stillborns create nothing; they stay a count on the breeding record.</li>
 * </ul>
 * A species set to GROUP tracking never gets individual animals from a birth.
 */
public final class AgBirthRules {

    public static final Set<String> NEWBORN_SEXES = Set.of("MALE", "FEMALE");
    public static final int MAX_TAG_LENGTH = 100;
    public static final int MAX_REMINDER_DAYS = 365;

    private AgBirthRules() {}

    /** What the farmer entered for one live offspring. */
    public record Offspring(String sex, String tagNumber, String name) {}

    /** The tag an offspring ends up with, and whether it is a temporary one. */
    public record Assigned(String tagNumber, boolean temporary) {}

    /** True unless the species is tracked as groups only. */
    public static boolean createsIndividualAnimals(String trackingMode) {
        return !"GROUP".equalsIgnoreCase(trackingMode == null ? "" : trackingMode.trim());
    }

    /** One entry per live offspring, each with a sex of MALE or FEMALE. Returns the sexes in upper case. */
    public static List<String> requireOffspringDetail(int live, List<Offspring> offspring) {
        int given = offspring == null ? 0 : offspring.size();
        if (given != live) {
            throw new IllegalArgumentException(live == 1
                    ? "Enter the sex of the live offspring"
                    : "Enter the sex of each of the " + live + " live offspring (" + given + " entered)");
        }
        List<String> sexes = new ArrayList<>();
        for (Offspring o : offspring == null ? List.<Offspring>of() : offspring) {
            String sex = o == null || o.sex() == null ? "" : o.sex().trim().toUpperCase(Locale.ROOT);
            if (!NEWBORN_SEXES.contains(sex)) throw new IllegalArgumentException("Each live offspring needs a sex of MALE or FEMALE");
            sexes.add(sex);
        }
        return sexes;
    }

    /** {@code <dam tag>-<yy>-<nn>}, for example 1234-26-01. */
    public static String temporaryTag(String damTag, LocalDate birthDate, int n) {
        return damTag.trim() + "-" + String.format("%02d", birthDate.getYear() % 100) + "-" + String.format("%02d", n);
    }

    /**
     * The tag for each offspring. A typed tag is used as typed; it must not already be on the farm and must not repeat inside this
     * birth. A blank one gets the next free temporary tag. {@code taken} is every tag already in use on the farm.
     */
    public static List<Assigned> assignTags(String damTag, LocalDate birthDate, List<String> requested, Set<String> taken) {
        Set<String> used = new HashSet<>(taken);
        List<String> typed = new ArrayList<>();
        for (String r : requested) {
            String t = r == null ? "" : r.trim();
            if (t.length() > MAX_TAG_LENGTH) throw new IllegalArgumentException("A tag number is at most " + MAX_TAG_LENGTH + " characters");
            typed.add(t);
        }
        for (String t : typed) {
            if (t.isEmpty()) continue;
            if (!used.add(t)) throw new IllegalArgumentException("Tag number '" + t + "' is already in use on this farm or repeated in this birth");
        }
        List<Assigned> out = new ArrayList<>();
        int n = 1;
        for (String t : typed) {
            if (!t.isEmpty()) { out.add(new Assigned(t, false)); continue; }
            String candidate = temporaryTag(damTag, birthDate, n);
            while (used.contains(candidate)) candidate = temporaryTag(damTag, birthDate, ++n);
            used.add(candidate);
            n++;
            out.add(new Assigned(candidate, true));
        }
        return out;
    }

    /** Days to wait before reminding the farmer to fit the permanent tag: empty (no reminder) or 1 to 365. */
    public static Integer requireReminderDays(Integer days) {
        if (days != null && (days < 1 || days > MAX_REMINDER_DAYS))
            throw new IllegalArgumentException("Days before the tag reminder must be between 1 and " + MAX_REMINDER_DAYS + ", or left empty for no reminder");
        return days;
    }

    /** Is the permanent-tag reminder due: a reminder is set, the animal has a birth date, and that many days have passed. */
    public static boolean reminderDue(LocalDate dateOfBirth, Integer reminderDays, LocalDate today) {
        return reminderDays != null && dateOfBirth != null && !dateOfBirth.plusDays(reminderDays).isAfter(today);
    }

    public static void requireTemporaryTag(boolean tagTemporary) {
        if (!tagTemporary) throw new IllegalStateException("Only a temporary tag can be replaced here");
    }

    public static String requireNewTag(String current, String newTag) {
        String t = newTag == null ? "" : newTag.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Enter the permanent tag number");
        if (t.length() > MAX_TAG_LENGTH) throw new IllegalArgumentException("A tag number is at most " + MAX_TAG_LENGTH + " characters");
        if (t.equals(current)) throw new IllegalArgumentException("That is already the animal's tag number");
        return t;
    }

    /** The reason written on a group's count log for the head added by a birth. */
    public static String groupCountReason(int live, LocalDate birthDate) {
        return "Birth: " + live + " born alive on " + birthDate;
    }
}
