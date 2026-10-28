package za.co.handyflow.platform.hr.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The fair-procedure checks for a disciplinary case. They follow the 2025 Code of Good Practice: Dismissal as described in law-firm
 * summaries (not the Code itself): the employee is told of the allegations in a form they can understand, has reasonable time to
 * prepare, may be helped by a union representative or fellow employee, and gets a decision with reasons. The Code does not fix hours of
 * notice, how long a warning lasts or how long an appeal may take, so those come from {@link ErPolicy}, the company's own settings.
 * <p>
 * Everything here is a rule about the case, never a decision: whether and how to discipline stays with the chair.
 */
public final class DisciplineRules {

    private DisciplineRules() {}

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    public static final String NOT_GUILTY = "NOT_GUILTY", COUNSELLING = "COUNSELLING", VERBAL = "VERBAL_WARNING",
            WRITTEN = "WRITTEN_WARNING", FINAL = "FINAL_WRITTEN_WARNING", DISMISSAL = "DISMISSAL", SUSPENSION = "SUSPENSION";

    /** What a chair can choose. SUSPENSION exists only on records imported from the old screen. */
    public static final List<String> SANCTIONS = List.of(COUNSELLING, VERBAL, WRITTEN, FINAL, DISMISSAL);
    public static final Set<String> WARNINGS = Set.of(VERBAL, WRITTEN, FINAL);
    /** Given after the employee has had the chance to respond, without a formal hearing. Anything stronger needs the full process. */
    public static final Set<String> WITHOUT_HEARING = Set.of(COUNSELLING, VERBAL, WRITTEN);
    /** The Labour Relations Act gives 30 days from the date of dismissal to refer a dispute to the CCMA. */
    public static final int CCMA_DAYS = 30;

    public static int severity(String sanction) {
        if (sanction == null) return -1;
        return switch (sanction) {
            case NOT_GUILTY -> 0;
            case COUNSELLING -> 1;
            case VERBAL -> 2;
            case WRITTEN -> 3;
            case FINAL -> 4;
            case SUSPENSION -> 4;
            case DISMISSAL -> 5;
            default -> -1;
        };
    }

    // ── Notice of hearing ─────────────────────────────────────────────────────────────────────────────

    /**
     * Null when the notice is complete and gives enough time. Used for a disciplinary notice (with charges) and for the invitation to an
     * incapacity meeting (without).
     */
    public static String noticeProblem(Instant noticeAt, LocalDate hearingDate, LocalTime hearingTime, String venue,
                                       List<String> charges, boolean chargesRequired, ErPolicy policy) {
        if (chargesRequired) {
            if (charges == null || charges.isEmpty()) return "State at least one charge, in plain language the employee can understand.";
            if (charges.size() > 20) return "No more than 20 charges can be put on one notice.";
            for (String c : charges) {
                if (c == null || c.isBlank()) return "A charge cannot be empty.";
                if (c.length() > 1000) return "A charge is too long (1000 characters at most).";
            }
        }
        if (hearingDate == null || hearingTime == null) return "Give the date and time of the meeting.";
        if (venue == null || venue.isBlank()) return "Give the venue, or the link if it will be held online.";
        if (venue.length() > 200) return "The venue is too long (200 characters at most).";
        Instant at = ZonedDateTime.of(hearingDate, hearingTime, ZONE).toInstant();
        if (!at.isAfter(noticeAt)) return "The meeting must be after the notice is given.";
        long hours = Duration.between(noticeAt, at).toHours();
        if (hours < policy.noticeHours())
            return "That is only " + hours + " hours' notice. Company policy gives at least " + policy.noticeHours() + " hours to prepare.";
        return null;
    }

    /** The chair must be someone other than the employee and the person bringing the charges. */
    public static String chairProblem(UUID chair, UUID employee, UUID initiator) {
        if (chair == null) return "Choose who will chair the hearing.";
        if (chair.equals(employee)) return "The employee cannot chair their own hearing.";
        if (chair.equals(initiator)) return "The person bringing the charges cannot chair the hearing.";
        return null;
    }

    /** Null when an informal sanction (given without a hearing) is allowed and has what it needs. */
    public static String informalProblem(String sanction, String employeeResponse, String reasons) {
        if (sanction == null || !WITHOUT_HEARING.contains(sanction))
            return "A final written warning or a dismissal can only follow a notice of hearing and a hearing.";
        if (employeeResponse == null || employeeResponse.isBlank())
            return "Record what the employee said when given the chance to respond. A sanction needs that first.";
        return ErRules.textProblem("The reasons", reasons, 2000);
    }

    // ── The hearing ───────────────────────────────────────────────────────────────────────────────────

    public static String hearingProblem(LocalDate heldOn, LocalDate noticeDate, LocalDate today, Boolean attended, String notes) {
        if (heldOn == null) return "Give the date the hearing was held.";
        if (heldOn.isAfter(today)) return "A hearing cannot be recorded before it has happened.";
        if (noticeDate != null && heldOn.isBefore(noticeDate)) return "The hearing cannot be held before the notice was given.";
        if (attended == null) return "Say whether the employee attended.";
        return ErRules.textProblem("The record of the hearing", notes, 8000);
    }

    // ── Findings and sanction ─────────────────────────────────────────────────────────────────────────

    /**
     * Null when the findings and sanction fit together. {@code findings} has one entry per charge (null where none is given yet).
     * {@code warningMonths} is what the chair settled on; the caller fills it from policy when the chair left it out.
     */
    public static String decisionProblem(List<String> findings, String sanction, Integer warningMonths, String reasons) {
        if (findings == null || findings.isEmpty()) return "There are no charges to give a finding on.";
        boolean guilty = false;
        for (String f : findings) {
            if (!"GUILTY".equals(f) && !"NOT_GUILTY".equals(f)) return "Give a finding on every charge.";
            if ("GUILTY".equals(f)) guilty = true;
        }
        String r = ErRules.textProblem("The reasons for the decision", reasons, 4000);
        if (r != null) return r;
        if (!guilty) {
            if (!NOT_GUILTY.equals(sanction)) return "The employee was found not guilty on every charge, so no sanction can be given.";
            return warningMonths == null ? null : "A warning cannot be given when the employee is found not guilty.";
        }
        if (sanction == null || !SANCTIONS.contains(sanction)) return "Choose the sanction.";
        if (WARNINGS.contains(sanction)) {
            if (warningMonths == null || warningMonths < 1 || warningMonths > 60) return "Say how many months the warning stays valid (1 to 60).";
        } else if (warningMonths != null) {
            return "Only a warning has a validity period.";
        }
        return null;
    }

    public record Warning(String sanction, LocalDate issuedOn, LocalDate validUntil) {}

    public static LocalDate validUntil(LocalDate issuedOn, int months) { return issuedOn.plusMonths(months); }

    /** Warnings in force on a date. A warning with no validity recorded cannot be relied on and is not counted. */
    public static List<Warning> active(List<Warning> all, LocalDate asOf) {
        List<Warning> out = new ArrayList<>();
        for (Warning w : all) {
            if (!WARNINGS.contains(w.sanction()) || w.validUntil() == null) continue;
            if (w.issuedOn().isAfter(asOf) || w.validUntil().isBefore(asOf)) continue;
            out.add(w);
        }
        return out;
    }

    /** Warnings that were given but have no validity recorded, so nobody can say whether they still count. */
    public static List<Warning> unknownValidity(List<Warning> all) {
        List<Warning> out = new ArrayList<>();
        for (Warning w : all) if ((WARNINGS.contains(w.sanction()) || SUSPENSION.equals(w.sanction())) && w.validUntil() == null) out.add(w);
        return out;
    }

    /** Plain advice to the chair from the warnings in force. It never decides: the facts and seriousness of this offence do. */
    public static String guidance(List<Warning> active) {
        int top = 0;
        for (Warning w : active) top = Math.max(top, severity(w.sanction()));
        String note = " A serious offence can justify more than the next step, and the chair decides on the facts.";
        if (top >= severity(FINAL))
            return "A final written warning is in force. A further offence of a similar kind can justify dismissal after a fair hearing." + note;
        if (top == severity(WRITTEN))
            return "A written warning is in force. The next step in progressive discipline is usually a final written warning." + note;
        if (top == severity(VERBAL))
            return "A verbal warning is in force. The next step in progressive discipline is usually a written warning." + note;
        return "No warning is in force. Start at the level this offence calls for." + note;
    }

    // ── Telling the employee, and appeals ─────────────────────────────────────────────────────────────

    public static String communicationProblem(String sanction, LocalDate on, LocalDate decidedOn, LocalDate today, boolean rightsExplained) {
        if (on == null) return "Give the date the employee was told of the outcome.";
        if (on.isAfter(today)) return "The date the employee was told cannot be in the future.";
        if (decidedOn != null && on.isBefore(decidedOn)) return "The employee cannot be told before the decision was made.";
        if (DISMISSAL.equals(sanction) && !rightsExplained)
            return "Record that the employee was told in writing of the right to refer a dismissal dispute to the CCMA within " + CCMA_DAYS + " days.";
        return null;
    }

    public static LocalDate ccmaDeadline(LocalDate dismissedOn) { return dismissedOn.plusDays(CCMA_DAYS); }

    public static LocalDate appealDeadline(LocalDate communicatedOn, ErPolicy policy) { return communicatedOn.plusDays(policy.appealDays()); }

    /** Late appeals are refused unless HR chooses to accept them. */
    public static String appealProblem(LocalDate communicatedOn, LocalDate lodgedOn, LocalDate today, String grounds, boolean accepted,
                                       ErPolicy policy) {
        if (communicatedOn == null) return "The outcome has not been communicated yet, so there is nothing to appeal.";
        if (lodgedOn == null) return "Give the date the appeal was lodged.";
        if (lodgedOn.isAfter(today)) return "The appeal date cannot be in the future.";
        if (lodgedOn.isBefore(communicatedOn)) return "An appeal cannot come before the outcome was communicated.";
        String g = ErRules.textProblem("The grounds of appeal", grounds, 4000);
        if (g != null) return g;
        if (lodgedOn.isAfter(appealDeadline(communicatedOn, policy)) && !accepted)
            return "The appeal period of " + policy.appealDays() + " days ended on " + appealDeadline(communicatedOn, policy)
                    + ". HR can still accept a late appeal and record why.";
        return null;
    }

    /** The appeal is heard by someone who was not part of the original decision. */
    public static String appealDeciderProblem(UUID decider, UUID employee, UUID chair, UUID initiator) {
        if (decider == null) return "Choose who will decide the appeal.";
        if (decider.equals(employee)) return "The employee cannot decide their own appeal.";
        if (Objects.equals(decider, chair)) return "The appeal must be decided by someone other than the chair of the hearing.";
        if (Objects.equals(decider, initiator)) return "The person who brought the charges cannot decide the appeal.";
        return null;
    }

    /** Null when the appeal decision is complete. An appeal can lower a sanction or overturn it, never raise it. */
    public static String appealOutcomeProblem(String outcome, String originalSanction, String newSanction, Integer warningMonths, String note) {
        if (!"CONFIRMED".equals(outcome) && !"VARIED".equals(outcome) && !"OVERTURNED".equals(outcome))
            return "Choose the appeal outcome: confirmed, varied or overturned.";
        String n = ErRules.textProblem("The reasons for the appeal decision", note, 4000);
        if (n != null) return n;
        if ("VARIED".equals(outcome)) {
            if (newSanction == null || !SANCTIONS.contains(newSanction)) return "Choose the lower sanction that replaces the original.";
            if (severity(newSanction) >= severity(originalSanction)) return "A varied sanction must be lower than the original.";
            if (WARNINGS.contains(newSanction) && (warningMonths == null || warningMonths < 1 || warningMonths > 60))
                return "Say how many months the replacement warning stays valid (1 to 60).";
        }
        return null;
    }

    // ── Ending employment after a dismissal ──────────────────────────────────────────────────────────

    /**
     * Null when employment may be ended because of this case. Only a dismissal that the employee was told of, and that no appeal is still
     * waiting on, ends employment. A dismissal the appeal overturned or lowered has a different outcome, so it is refused here.
     */
    public static String endEmploymentProblem(String status, String outcome, LocalDate communicatedOn, String employeeStatus, LocalDate lastDay) {
        if (!DISMISSAL.equals(outcome)) return "Only a case that ended in dismissal can end the employee's employment.";
        if (ErRules.APPEALED.equals(status)) return "An appeal is waiting for a decision. Employment cannot be ended until it is decided.";
        if (!ErRules.DECIDED.equals(status) && !ErRules.CLOSED.equals(status)) return "The case has no decision yet.";
        if (communicatedOn == null) return "Tell the employee the outcome before ending their employment.";
        if ("TERMINATED".equals(employeeStatus)) return "This employee's employment has already ended.";
        if (lastDay == null) return "Give the last day of employment.";
        if (lastDay.isBefore(communicatedOn)) return "The last day cannot be before the employee was told of the dismissal (" + communicatedOn + ").";
        return null;
    }

    /**
     * Null when the answers about how a dismissal ends employment are usable. A dismissal ends employment with notice (the employer gives it,
     * or pays for the days not given) or summarily, which is for serious misconduct. Notice is taken to have been given the day the employee
     * was told, unless a later day is given; a summary dismissal has no notice date.
     */
    public static String endTypeProblem(String endType, LocalDate noticeGivenOn, LocalDate communicatedOn, LocalDate lastDay) {
        if (!NoticeRules.EMPLOYER_NOTICE.equals(endType) && !NoticeRules.SUMMARY_DISMISSAL.equals(endType))
            return "Say how the dismissal ends employment: with notice, or summarily.";
        if (NoticeRules.SUMMARY_DISMISSAL.equals(endType)) return noticeGivenOn != null ? "A summary dismissal has no notice date." : null;
        if (noticeGivenOn == null) return null;
        if (communicatedOn != null && noticeGivenOn.isBefore(communicatedOn)) return "Notice cannot be given before the employee was told of the dismissal (" + communicatedOn + ").";
        if (lastDay != null && noticeGivenOn.isAfter(lastDay)) return "Notice cannot be given after the last day worked.";
        return null;
    }

    /** The day notice counts from: the one given, else the day the employee was told. Null for anything but a dismissal with notice. */
    public static LocalDate noticeDay(String endType, LocalDate noticeGivenOn, LocalDate communicatedOn) {
        return NoticeRules.EMPLOYER_NOTICE.equals(endType) ? (noticeGivenOn != null ? noticeGivenOn : communicatedOn) : null;
    }

    /** Null when a warning that was recorded without a validity can be given one. */
    public static String validityProblem(String outcome, LocalDate decidedOn, LocalDate currentValidUntil, Integer months) {
        if (!WARNINGS.contains(outcome)) return "Only a warning has a validity.";
        if (decidedOn == null) return "This warning has no date, so its validity cannot be worked out.";
        if (currentValidUntil != null) return "This warning already has a validity (until " + currentValidUntil + ").";
        if (months == null || months < 1 || months > 60) return "Say how many months the warning stays valid (1 to 60).";
        return null;
    }

    // ── Precautionary suspension ──────────────────────────────────────────────────────────────────────

    /** A precautionary suspension is always on full pay and needs a reason. Suspension without pay is not offered. */
    public static String suspensionProblem(LocalDate from, LocalDate to, String reason, LocalDate today) {
        if (from == null) return "Give the date the suspension starts.";
        if (from.isAfter(today.plusDays(7))) return "The suspension cannot start more than a week from now.";
        if (to != null && to.isBefore(from)) return "The suspension cannot end before it starts.";
        return ErRules.textProblem("The reason for the suspension", reason, 2000);
    }
}
