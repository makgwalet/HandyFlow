package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Performance: review cycles, goals, 360 feedback and improvement plans. Who may do what, what each person may see, and what makes a
 * request acceptable. No database, clock or framework in here: dates and flags come in as arguments.
 *
 * Roles used throughout: the <em>subject</em> is the employee being reviewed; the <em>reviewer</em> (or <em>manager</em>) is the employee
 * who reviews them; <em>HR</em> holds HR_PERF_MANAGE. A person who is the subject is never treated as HR or reviewer for their own record.
 */
public final class PerfRules {

    private PerfRules() {}

    // ── Permissions ──────────────────────────────────────────────────────────────────────────────────
    public static boolean canRead(Set<String> held) { return held.contains("HR_PERF_READ") || held.contains("HR_PERF_MANAGE"); }
    public static boolean canManage(Set<String> held) { return held.contains("HR_PERF_MANAGE"); }

    // ── Cycles ───────────────────────────────────────────────────────────────────────────────────────
    public static final String DRAFT = "DRAFT", OPEN = "OPEN", CLOSED = "CLOSED";

    public static String cycleProblem(String name, LocalDate start, LocalDate end, LocalDate selfDue, LocalDate managerDue) {
        String n = text("The cycle name", name, 3, 120);
        if (n != null) return n;
        if (start == null || end == null) return "Give the start and end of the period being reviewed.";
        if (end.isBefore(start)) return "The period cannot end before it starts.";
        if (ChronoUnit.DAYS.between(start, end) > 400) return "A review period longer than about a year is too long.";
        if (selfDue == null || managerDue == null) return "Give the dates by which the self-review and the manager review are due.";
        if (selfDue.isBefore(start)) return "The self-review cannot be due before the period starts.";
        if (managerDue.isBefore(selfDue)) return "The manager review cannot be due before the self-review.";
        return null;
    }

    // ── Ratings ──────────────────────────────────────────────────────────────────────────────────────
    public static final List<String> RATING_LABELS = List.of("Unsatisfactory", "Needs improvement", "Meets expectations", "Exceeds expectations", "Outstanding");

    public static String ratingLabel(Integer rating) { return rating == null || rating < 1 || rating > 5 ? null : RATING_LABELS.get(rating - 1); }

    private static String ratingProblem(Integer rating, String what) {
        return rating == null || rating < 1 || rating > 5 ? "Give " + what + " from 1 to 5." : null;
    }

    public static String selfProblem(Integer rating, String comment) {
        String r = ratingProblem(rating, "your rating");
        if (r != null) return r;
        if (comment != null && comment.length() > 4000) return "Your comments are too long (4000 characters at most).";
        return null;
    }

    /** The manager must say why. A low rating, or an outstanding one, needs real reasons. */
    public static String managerProblem(Integer rating, String comment) {
        String r = ratingProblem(rating, "the rating");
        if (r != null) return r;
        String c = text("Your comments", comment, 3, 4000);
        if (c != null) return c;
        if ((rating <= 2 || rating == 5) && comment.trim().length() < 20) return "A rating of " + rating + " needs the reasons in more detail (at least 20 characters).";
        return null;
    }

    public static String calibrationProblem(Integer rating, String reason, Integer current) {
        String r = ratingProblem(rating, "the final rating");
        if (r != null) return r;
        if (rating.equals(current)) return "That is already the final rating.";
        return text("The reason for changing the rating", reason, 10, 1000);
    }

    // ── Reviews ──────────────────────────────────────────────────────────────────────────────────────
    public static final String AWAITING_SELF = "AWAITING_SELF", AWAITING_MANAGER = "AWAITING_MANAGER", SUBMITTED = "SUBMITTED",
            SHARED = "SHARED", ACKNOWLEDGED = "ACKNOWLEDGED", EXCLUDED = "EXCLUDED";

    public static final String A_SELF = "SELF", A_MANAGER = "MANAGER", A_REVIEWER = "REVIEWER", A_CALIBRATE = "CALIBRATE", A_SHARE = "SHARE",
            A_EXCLUDE = "EXCLUDE", A_ACKNOWLEDGE = "ACKNOWLEDGE", A_FEEDBACK = "REQUEST_FEEDBACK";

    public record Who(boolean subject, boolean reviewer, boolean hr) {
        /** The subject is only ever the subject, whatever else they hold. */
        public static Who of(boolean subject, boolean reviewer, boolean hr) { return subject ? new Who(true, false, false) : new Who(false, reviewer, hr); }
        public boolean any() { return subject || reviewer || hr; }
    }

    private static boolean working(String status) { return AWAITING_SELF.equals(status) || AWAITING_MANAGER.equals(status) || SUBMITTED.equals(status); }

    /** What the person may do to a review right now. */
    public static List<String> reviewActions(Who w, String reviewStatus, String cycleStatus, boolean include360, boolean reviewerSet, LocalDate selfDue, LocalDate today) {
        List<String> a = new ArrayList<>();
        boolean open = OPEN.equals(cycleStatus);
        if (w.subject() && open && AWAITING_SELF.equals(reviewStatus)) a.add(A_SELF);
        if (w.reviewer() && open && (AWAITING_MANAGER.equals(reviewStatus) || (AWAITING_SELF.equals(reviewStatus) && today.isAfter(selfDue)))) a.add(A_MANAGER);
        if (w.hr() && open && (AWAITING_SELF.equals(reviewStatus) || AWAITING_MANAGER.equals(reviewStatus))) a.add(A_REVIEWER);
        if (w.hr() && SUBMITTED.equals(reviewStatus)) { a.add(A_CALIBRATE); a.add(A_SHARE); }
        if (w.hr() && working(reviewStatus)) a.add(A_EXCLUDE);
        if (w.subject() && SHARED.equals(reviewStatus)) a.add(A_ACKNOWLEDGE);
        if ((w.hr() || w.reviewer()) && open && include360 && reviewerSet && working(reviewStatus)) a.add(A_FEEDBACK);
        return a;
    }

    public record Sight(boolean self, boolean manager, boolean feedback, boolean feedbackNames) {}

    /** What each person may read of a review. An employee sees the manager's side and the 360 summary only after the result is released. */
    public static Sight sight(Who w, String reviewStatus) {
        boolean released = SHARED.equals(reviewStatus) || ACKNOWLEDGED.equals(reviewStatus);
        if (w.subject()) return new Sight(true, released, released, false);
        if (w.hr()) return new Sight(true, true, true, true);
        if (w.reviewer()) return new Sight(true, true, true, false);
        return new Sight(false, false, false, false);
    }

    public static String acknowledgeProblem(String comment) {
        return comment != null && comment.length() > 4000 ? "Your comments are too long (4000 characters at most)." : null;
    }

    public static String reviewerProblem(java.util.UUID reviewerId, java.util.UUID subjectId, String reason) {
        if (reviewerId == null) return "Choose the reviewer.";
        if (reviewerId.equals(subjectId)) return "Nobody can review themselves.";
        return text("The reason for changing the reviewer", reason, 3, 500);
    }

    public static String excludeProblem(String reason) { return text("The reason for leaving this review out", reason, 3, 500); }

    /** Null when the cycle may be closed. Counts are of reviews still being worked on. */
    public static String closeCycleProblem(long unfinished) {
        return unfinished > 0 ? unfinished + (unfinished == 1 ? " review is" : " reviews are") + " not released yet. Release them, or leave them out with a reason." : null;
    }

    // ── 360 feedback ─────────────────────────────────────────────────────────────────────────────────
    public static final List<String> RELATIONSHIPS = List.of("PEER", "DIRECT_REPORT", "OTHER");
    /** Fewest replies before the summary is shown. With fewer, a reader could work out who said what. */
    public static final int MIN_REPLIES = 3;
    public static final int MAX_PROVIDERS = 15;

    public static String requestProblem(java.util.UUID providerId, java.util.UUID subjectId, java.util.UUID reviewerId, boolean alreadyAsked, int askedSoFar, String relationship) {
        if (providerId == null) return "Choose who to ask.";
        if (providerId.equals(subjectId)) return "Someone cannot give feedback about themselves.";
        if (providerId.equals(reviewerId)) return "The reviewer's view goes in the review itself.";
        if (alreadyAsked) return "That person has already been asked.";
        if (askedSoFar >= MAX_PROVIDERS) return "At most " + MAX_PROVIDERS + " people can be asked about one person.";
        if (!RELATIONSHIPS.contains(relationship)) return "Say how this person works with the employee.";
        return null;
    }

    public static String feedbackProblem(Integer rating, String strengths, String improvements) {
        String r = ratingProblem(rating, "an overall rating");
        if (r != null) return r;
        boolean s = strengths != null && strengths.trim().length() >= 10, i = improvements != null && improvements.trim().length() >= 10;
        if (!s && !i) return "Say something specific: what the person does well, or what could be better (at least 10 characters).";
        if ((strengths != null && strengths.length() > 2000) || (improvements != null && improvements.length() > 2000)) return "Each comment is limited to 2000 characters.";
        return null;
    }

    public record Reply(int rating, String strengths, String improvements) {}
    public record Summary(boolean shown, int replies, Double average, List<String> strengths, List<String> improvements) {}

    /** The 360 summary. Hidden until enough people have replied; no names, no dates, and the comments are in alphabetical order. */
    public static Summary summarise(List<Reply> replies) {
        int n = replies.size();
        if (n < MIN_REPLIES) return new Summary(false, n, null, List.of(), List.of());
        double avg = replies.stream().mapToInt(Reply::rating).average().orElse(0);
        Double rounded = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
        List<String> s = replies.stream().map(Reply::strengths).filter(t -> t != null && !t.isBlank()).map(String::trim).sorted(Comparator.comparing(String::toLowerCase)).toList();
        List<String> i = replies.stream().map(Reply::improvements).filter(t -> t != null && !t.isBlank()).map(String::trim).sorted(Comparator.comparing(String::toLowerCase)).toList();
        return new Summary(true, n, rounded, s, i);
    }

    public static String declineProblem(String reason) { return reason != null && reason.length() > 500 ? "The reason is too long (500 characters at most)." : null; }

    // ── Goals ────────────────────────────────────────────────────────────────────────────────────────
    public static final String PROPOSED = "PROPOSED", ACTIVE = "ACTIVE", ACHIEVED = "ACHIEVED", PARTLY_ACHIEVED = "PARTLY_ACHIEVED",
            NOT_ACHIEVED = "NOT_ACHIEVED", CANCELLED = "CANCELLED";
    public static final List<String> GOAL_OUTCOMES = List.of(ACHIEVED, PARTLY_ACHIEVED, NOT_ACHIEVED);

    public static String goalProblem(String title, String description, String measure, LocalDate due, LocalDate today, boolean isNew) {
        String t = text("The goal", title, 3, 200);
        if (t != null) return t;
        if (description != null && description.length() > 4000) return "The description is too long (4000 characters at most).";
        if (measure != null && measure.length() > 500) return "How success is measured is too long (500 characters at most).";
        if (due == null) return "Give the date the goal is due.";
        if (isNew && due.isBefore(today)) return "A new goal cannot be due in the past.";
        if (due.isAfter(today.plusYears(5))) return "A goal due more than five years away is not a goal for this review.";
        return null;
    }

    public static String progressProblem(Integer progress, String note) {
        if (progress == null || progress < 0 || progress > 100) return "Progress is a whole number from 0 to 100.";
        return note != null && note.length() > 1000 ? "The note is too long (1000 characters at most)." : null;
    }

    public static String goalCloseProblem(String outcome, String note) {
        if (!GOAL_OUTCOMES.contains(outcome)) return "Choose how the goal ended.";
        if (!ACHIEVED.equals(outcome)) return text("What happened", note, 5, 2000);
        return note != null && note.length() > 2000 ? "The note is too long (2000 characters at most)." : null;
    }

    public static String goalCancelProblem(String reason) { return text("The reason for cancelling the goal", reason, 3, 500); }

    public static final String G_EDIT = "EDIT", G_APPROVE = "APPROVE", G_PROGRESS = "PROGRESS", G_CLOSE = "CLOSE", G_CANCEL = "CANCEL";

    /** For goals, HR means HR_PERF_MANAGE and the manager is whoever the employee reports to now. */
    public static List<String> goalActions(Who w, String status) {
        List<String> a = new ArrayList<>();
        boolean boss = w.reviewer() || w.hr();
        if (PROPOSED.equals(status)) {
            if (w.subject() || boss) a.add(G_EDIT);
            if (boss) a.add(G_APPROVE);
            if (w.subject() || boss) a.add(G_CANCEL);
        } else if (ACTIVE.equals(status)) {
            if (boss) a.add(G_EDIT);
            if (w.subject() || boss) a.add(G_PROGRESS);
            if (boss) { a.add(G_CLOSE); a.add(G_CANCEL); }
        }
        return a;
    }

    public static String initialGoalStatus(boolean subject) { return subject ? PROPOSED : ACTIVE; }

    // ── Improvement plans ────────────────────────────────────────────────────────────────────────────
    public static final String PLAN_DRAFT = "DRAFT", PLAN_ACTIVE = "ACTIVE", MET = "MET", NOT_MET = "NOT_MET", PLAN_CANCELLED = "CANCELLED";
    public static final List<String> CHECKIN_PROGRESS = List.of("ON_TRACK", "AT_RISK", "OFF_TRACK");

    public static String planProblem(String title, String shortfall, String standard, String support, LocalDate start, LocalDate end) {
        String t = text("The plan's title", title, 3, 200);
        if (t != null) return t;
        String s = text("What the shortfall is", shortfall, 10, 4000);
        if (s != null) return s;
        String d = text("The standard required", standard, 10, 4000);
        if (d != null) return d;
        String p = text("The support the company will give", support, 10, 4000);
        if (p != null) return p;
        if (start == null || end == null) return "Give the start and end of the plan.";
        if (end.isBefore(start)) return "The plan cannot end before it starts.";
        if (ChronoUnit.DAYS.between(start, end) > 365) return "A plan longer than a year is too long. Set shorter steps.";
        return null;
    }

    public static String startProblem(LocalDate communicatedOn, LocalDate today) {
        if (communicatedOn == null) return "Give the date the employee was told about the plan.";
        if (communicatedOn.isAfter(today)) return "The employee cannot have been told on a future date.";
        return null;
    }

    public static String checkinProblem(LocalDate on, LocalDate start, LocalDate today, String progress, String note) {
        if (on == null) return "Give the date of the check-in.";
        if (on.isBefore(start)) return "A check-in cannot be before the plan starts.";
        if (on.isAfter(today)) return "A check-in cannot be recorded before it happens.";
        if (!CHECKIN_PROGRESS.contains(progress)) return "Say whether the employee is on track, at risk or off track.";
        return text("What was discussed", note, 10, 4000);
    }

    public static String extendProblem(LocalDate newEnd, LocalDate oldEnd, String reason) {
        if (newEnd == null || !newEnd.isAfter(oldEnd)) return "The new end date must be later than " + oldEnd + ".";
        if (ChronoUnit.DAYS.between(oldEnd, newEnd) > 365) return "Extend by at most a year at a time.";
        return text("The reason for giving more time", reason, 5, 1000);
    }

    /** Ending a plan as not met is a serious step: the time promised must have run, and there must be a record of how it went. */
    public static String closePlanProblem(String outcome, String note, LocalDate today, LocalDate end, long checkins) {
        if (!MET.equals(outcome) && !NOT_MET.equals(outcome)) return "Choose whether the plan was met or not met.";
        String n = text("The outcome", note, 10, 2000);
        if (n != null) return n;
        if (NOT_MET.equals(outcome)) {
            if (today.isBefore(end)) return "The plan runs until " + end + ". The employee must have all the time they were promised before it is ended as not met.";
            if (checkins < 1) return "Record at least one check-in before ending a plan as not met.";
        }
        return null;
    }

    public static String cancelPlanProblem(String reason) { return text("The reason for cancelling the plan", reason, 3, 500); }

    /** True when the plan gives less time than the company's minimum review period for incapacity. */
    public static boolean shorterThanMinimum(LocalDate start, LocalDate end, int minDays) {
        return start != null && end != null && ChronoUnit.DAYS.between(start, end) + 1 < minDays;
    }

    public static String caseLinkProblem(String type, String category, String status, java.util.UUID caseEmployee, java.util.UUID planEmployee) {
        if (!ErRules.INCAPACITY.equals(type) || !"POOR_PERFORMANCE".equals(category)) return "An improvement plan can only be linked to a poor performance (incapacity) case.";
        if (ErRules.CLOSED.equals(status) || ErRules.WITHDRAWN.equals(status)) return "That case is closed.";
        if (!planEmployee.equals(caseEmployee)) return "That case is about a different employee.";
        return null;
    }

    public static final String P_EDIT = "EDIT", P_START = "START", P_CHECKIN = "CHECKIN", P_EXTEND = "EXTEND", P_CLOSE = "CLOSE", P_CANCEL = "CANCEL",
            P_ACKNOWLEDGE = "ACKNOWLEDGE", P_LINK = "LINK";

    /** For plans, HR means HR_PERF_MANAGE and the reviewer flag means the plan's owner. */
    public static List<String> planActions(Who w, String status, boolean acknowledged) {
        List<String> a = new ArrayList<>();
        boolean open = PLAN_DRAFT.equals(status) || PLAN_ACTIVE.equals(status);
        if (w.hr() && PLAN_DRAFT.equals(status)) { a.add(P_EDIT); a.add(P_START); }
        if ((w.hr() || w.reviewer()) && PLAN_ACTIVE.equals(status)) a.add(P_CHECKIN);
        if (w.hr() && PLAN_ACTIVE.equals(status)) { a.add(P_EXTEND); a.add(P_CLOSE); }
        if (w.hr() && open) { a.add(P_LINK); a.add(P_CANCEL); }
        if (w.subject() && !acknowledged && (PLAN_ACTIVE.equals(status) || MET.equals(status) || NOT_MET.equals(status))) a.add(P_ACKNOWLEDGE);
        return a;
    }

    /** The employee sees a plan only once they have been told about it. HR with read access and the owner see every plan. */
    public static boolean canSeePlan(Who w, boolean hrRead, boolean owner, boolean communicated) {
        if (w.subject()) return communicated;
        return hrRead || owner;
    }

    // ── Shared ───────────────────────────────────────────────────────────────────────────────────────
    private static String text(String what, String value, int min, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return what + " is required.";
        if (v.length() < min) return what + " is too short (at least " + min + " characters).";
        if (v.length() > max) return what + " is too long (" + max + " characters at most).";
        return null;
    }
}
