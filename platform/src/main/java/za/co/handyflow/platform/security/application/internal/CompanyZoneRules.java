package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.BusinessTime;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;

/** Pure rules for changing the time zone a company's days, weeks and months are decided in. */
public final class CompanyZoneRules {

    /** The same shape the database accepts (V861). */
    private static final Pattern SHAPE = Pattern.compile("^[A-Za-z][A-Za-z0-9_+-]*(/[A-Za-z0-9_+-]+){0,2}$");

    /** What changes, for the person about to confirm. */
    public record Effect(String from, String to, int offsetChangeMinutes, boolean todayChangesNow, String warning) {}

    private CompanyZoneRules() {}

    /**
     * A real region zone (Africa/Windhoek, Europe/London, UTC). A bare offset such as +02:00 is refused: it has no clock-change rules and no name
     * a person would recognise. The result is the canonical id as written by the zone database.
     */
    public static String requireValid(String raw) {
        String id = raw == null ? "" : raw.strip();
        if (id.isEmpty()) throw bad("Choose a time zone.", "TZ_REQUIRED");
        if (id.length() > 60 || !SHAPE.matcher(id).matches() || !ZoneId.getAvailableZoneIds().contains(id)) {
            throw bad("That is not a known time zone. Use a region name such as Africa/Johannesburg.", "TZ_INVALID");
        }
        return id;
    }

    /** A change to the zone the company already has is refused, so nothing is audited for a no-op. */
    public static void requireDifferent(String current, String wanted) {
        if (BusinessTime.zoneOrDefault(current).getId().equals(wanted)) {
            throw new HandyFlowException("The company is already on that time zone.", HttpStatus.CONFLICT, "TZ_UNCHANGED");
        }
    }

    /** A change must be confirmed after the person has seen {@link #effect}. */
    public static void requireConfirmed(boolean confirm) {
        if (!confirm) {
            throw new HandyFlowException("Confirm the change after reading what it affects.", HttpStatus.CONFLICT, "TZ_CONFIRM_REQUIRED");
        }
    }

    public static Effect effect(ZoneId from, ZoneId to, Instant now) {
        ZoneId f = from == null ? BusinessTime.DEFAULT_ZONE : from;
        int fromOffset = f.getRules().getOffset(now).getTotalSeconds();
        int toOffset = to.getRules().getOffset(now).getTotalSeconds();
        int change = (toOffset - fromOffset) / 60;
        boolean todayChanges = !BusinessTime.today(f, now).equals(BusinessTime.today(to, now));
        StringBuilder w = new StringBuilder("From now on days, weeks and months start and end in ").append(to.getId()).append(". ");
        if (change == 0) w.append("The clock offset is the same right now, but clock changes differ. ");
        else w.append("The clock moves by ").append(describe(change)).append(". ");
        if (todayChanges) w.append("It is a different date there right now. ");
        w.append("Hours, coverage and reports for days already recorded are not rewritten: figures already approved or sent stay as they are, ")
                .append("but a month shown again is cut at the new midnight, so a shift near midnight can move to the next or previous day. ")
                .append("Dates in a payroll export for an old period are shown in the new zone.");
        return new Effect(f.getId(), to.getId(), change, todayChanges, w.toString());
    }

    private static String describe(int minutes) {
        int abs = Math.abs(minutes);
        String dir = minutes > 0 ? "forward" : "back";
        String t = abs % 60 == 0 ? (abs / 60) + (abs == 60 ? " hour" : " hours") : (abs / 60) + " h " + (abs % 60) + " min";
        return t + " " + dir;
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
}
