package za.co.handyflow.platform.earthmoving.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The inspection walk-round, done before a machine is used, when it is handed to a customer and when it comes back. Every item on the checklist must be answered. A defect or an unsafe finding needs a comment
 * saying what was seen. One UNSAFE finding makes the whole inspection UNSAFE: the machine is not to be used until a
 * person with the authority has looked at it and released it.
 */
public final class InspectionRules {
    private InspectionRules() {}

    public static final String OK = "OK";
    public static final String DEFECT = "DEFECT";
    public static final String UNSAFE = "UNSAFE";
    public static final Set<String> OUTCOMES = Set.of(OK, DEFECT, UNSAFE);

    public static final String PRE_USE = "PRE_USE";
    public static final String HANDOVER = "HANDOVER";
    public static final String RETURN = "RETURN";
    public static final String MAINTENANCE = "MAINTENANCE";
    public static final String SAFETY = "SAFETY";
    public static final Set<String> TYPES = Set.of(PRE_USE, HANDOVER, RETURN, MAINTENANCE, SAFETY);

    /** Blank means the usual pre-use walk-round. */
    public static String type(String t) {
        if (t == null || t.isBlank()) return PRE_USE;
        String v = t.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(v)) throw new IllegalArgumentException("An inspection is a PRE_USE, HANDOVER, RETURN, MAINTENANCE or SAFETY inspection.");
        return v;
    }

    /** A handover or a return records the meter, so the hours or kilometres of the hire can be shown. */
    public static void checkMeter(String type, java.math.BigDecimal meterReading) {
        if ((HANDOVER.equals(type) || RETURN.equals(type)) && meterReading == null)
            throw new IllegalArgumentException("Enter the meter reading for a " + (HANDOVER.equals(type) ? "handover" : "return") + " inspection.");
    }

    public static final String PASS = "PASS";
    public static final String DEFECTS = "DEFECTS";

    /** The minimum every machine is checked against. A company can add to it later; it cannot take these away. */
    public static final List<String> CHECKLIST = List.of(
            "Tyres or tracks", "Brakes", "Steering", "Hydraulic hoses and leaks", "Lights, horn and reverse alarm",
            "Seat belt and roll-over protection", "Fire extinguisher", "Engine oil, coolant and fuel", "Cab glass and mirrors",
            "Guards and covers in place");

    public record Answer(String item, String outcome, String comment) {}

    public static String outcome(String o) {
        String v = o == null ? "" : o.trim().toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(v)) throw new IllegalArgumentException("Each item is OK, a DEFECT or UNSAFE.");
        return v;
    }

    /** Returns the cleaned answers in checklist order, or throws saying what is missing. */
    public static List<Answer> check(List<Answer> given) {
        if (given == null || given.isEmpty()) throw new IllegalArgumentException("Answer every item on the checklist.");
        List<Answer> out = new ArrayList<>();
        for (String item : CHECKLIST) {
            Answer a = given.stream().filter(x -> x.item() != null && x.item().trim().equalsIgnoreCase(item)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Answer every item on the checklist: \"" + item + "\" is missing."));
            String o = outcome(a.outcome());
            String c = a.comment() == null ? "" : a.comment().trim();
            if (!OK.equals(o) && c.length() < 5) throw new IllegalArgumentException("Say what was seen on \"" + item + "\" (at least 5 characters).");
            out.add(new Answer(item, o, c.isEmpty() ? null : c));
        }
        return out;
    }

    public static String overall(List<Answer> answers) {
        if (answers.stream().anyMatch(a -> UNSAFE.equals(a.outcome()))) return UNSAFE;
        if (answers.stream().anyMatch(a -> DEFECT.equals(a.outcome()))) return DEFECTS;
        return PASS;
    }
}
