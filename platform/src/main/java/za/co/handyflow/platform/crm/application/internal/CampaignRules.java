package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.CampaignStatus;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.MemberStatus;
import za.co.handyflow.platform.crm.domain.model.WorkflowAction;

/** Rules for campaigns and their journeys: what is allowed, who is due for which step, and the numbers. Pure, no database. */
final class CampaignRules {
    static final int MAX_CAMPAIGNS = 50, MAX_STEPS = 10, MAX_MEMBERS = 2000, MAX_ADD_AT_ONCE = 500;

    /** One step of a journey: do this many days after the person was added. */
    record Step(int dayOffset, WorkflowAction action, String text, UUID user) {}

    /** Counts for a campaign. Rates are whole percentages of everyone added, or null while nobody has been added. */
    record Summary(int total, int contacted, int responded, int converted, Integer responseRate, Integer conversionRate) {}

    private CampaignRules() {}

    static void check(String name, String goal, LocalDate startsOn, LocalDate endsOn) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the campaign");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
        if (goal != null && goal.strip().length() > 500) throw new IllegalArgumentException("The goal can be at most 500 characters");
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) throw new IllegalArgumentException("The end date cannot be before the start date");
    }

    /** Draft starts it; active can pause or finish; paused can resume or finish; finished stays finished. */
    static void checkMove(CampaignStatus from, CampaignStatus to) {
        if (to == null) throw new IllegalArgumentException("Choose a status");
        if (from == to) return;
        boolean ok = switch (from) {
            case DRAFT -> to == CampaignStatus.ACTIVE;
            case ACTIVE -> to == CampaignStatus.PAUSED || to == CampaignStatus.COMPLETED;
            case PAUSED -> to == CampaignStatus.ACTIVE || to == CampaignStatus.COMPLETED;
            case COMPLETED -> false;
        };
        if (!ok) throw new IllegalArgumentException(from == CampaignStatus.COMPLETED ? "A finished campaign cannot be started again" : "Cannot move a campaign from " + from.name().toLowerCase() + " to " + to.name().toLowerCase());
    }

    /** Steps can only be changed while nothing is running them. */
    static void checkEditable(CampaignStatus status) {
        if (status == CampaignStatus.ACTIVE) throw new IllegalArgumentException("Pause the campaign before changing its steps");
        if (status == CampaignStatus.COMPLETED) throw new IllegalArgumentException("A finished campaign cannot be changed");
    }

    static void checkSteps(List<Step> steps) {
        if (steps == null) throw new IllegalArgumentException("Send the list of steps");
        if (steps.size() > MAX_STEPS) throw new IllegalArgumentException("A journey can have at most " + MAX_STEPS + " steps");
        int previous = 0;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String where = "Step " + (i + 1) + ": ";
            if (s.dayOffset() < 0 || s.dayOffset() > 365) throw new IllegalArgumentException(where + "the day must be between 0 and 365");
            if (s.dayOffset() < previous) throw new IllegalArgumentException(where + "it cannot come before the step above it");
            previous = s.dayOffset();
            if (s.action() == null) throw new IllegalArgumentException(where + "choose what it does");
            String t = s.text() == null ? "" : s.text().strip();
            switch (s.action()) {
                case CREATE_TASK -> { if (t.isEmpty()) throw new IllegalArgumentException(where + "say what the task is"); if (t.length() > 500) throw new IllegalArgumentException(where + "the task can be at most 500 characters"); }
                case ADD_TAG -> { if (t.isEmpty()) throw new IllegalArgumentException(where + "name the tag"); if (t.length() > 50) throw new IllegalArgumentException(where + "the tag can be at most 50 characters"); }
                case ASSIGN_OWNER -> { if (s.user() == null) throw new IllegalArgumentException(where + "choose who becomes the owner"); }
                case NOTIFY_OWNER -> { if (t.isEmpty()) throw new IllegalArgumentException(where + "write the message"); if (t.length() > 500) throw new IllegalArgumentException(where + "the message can be at most 500 characters"); }
            }
        }
    }

    /** The positions (0-based, in order) of the steps now due for someone added on that date who has already had stepsDone steps. */
    static List<Integer> dueSteps(List<Step> steps, int stepsDone, LocalDate addedOn, LocalDate today) {
        List<Integer> due = new ArrayList<>();
        for (int i = Math.max(0, stepsDone); i < steps.size(); i++) {
            if (addedOn.plusDays(steps.get(i).dayOffset()).isAfter(today)) break;
            due.add(i);
        }
        return due;
    }

    /** The journey stops for someone who replied, converted, or whose lead has closed. */
    static boolean exits(MemberStatus status, LeadStage stage) {
        return status == MemberStatus.RESPONDED || status == MemberStatus.CONVERTED || stage == LeadStage.WON || stage == LeadStage.LOST;
    }

    /** True while the campaign is in its dates (a missing date is open-ended). */
    static boolean inWindow(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
        return (startsOn == null || !today.isBefore(startsOn)) && (endsOn == null || !today.isAfter(endsOn));
    }

    static Summary summarise(Collection<MemberStatus> statuses) {
        java.util.Map<MemberStatus, Integer> counts = new java.util.EnumMap<>(MemberStatus.class);
        for (MemberStatus s : statuses) counts.merge(s, 1, Integer::sum);
        return summarise(counts);
    }

    /** From how many people are at each status. */
    static Summary summarise(java.util.Map<MemberStatus, Integer> counts) {
        int added = counts.getOrDefault(MemberStatus.ADDED, 0), contactedOnly = counts.getOrDefault(MemberStatus.CONTACTED, 0);
        int respondedOnly = counts.getOrDefault(MemberStatus.RESPONDED, 0), converted = counts.getOrDefault(MemberStatus.CONVERTED, 0);
        int total = added + contactedOnly + respondedOnly + converted, responded = respondedOnly + converted, contacted = contactedOnly + responded;
        return new Summary(total, contacted, responded, converted, total == 0 ? null : Math.round(responded * 100f / total), total == 0 ? null : Math.round(converted * 100f / total));
    }

    /** The people from the request who are not already in, without repeats, in order. Refuses when the campaign would grow past its limit. */
    static List<UUID> toAdd(Set<UUID> alreadyIn, List<UUID> requested) {
        if (requested == null || requested.isEmpty()) throw new IllegalArgumentException("Choose who to add");
        Set<UUID> fresh = new LinkedHashSet<>(requested);
        fresh.removeAll(alreadyIn);
        if (fresh.size() > MAX_ADD_AT_ONCE) throw new IllegalArgumentException("Add at most " + MAX_ADD_AT_ONCE + " people at a time");
        if (alreadyIn.size() + fresh.size() > MAX_MEMBERS) throw new IllegalArgumentException("A campaign can have at most " + MAX_MEMBERS + " people");
        return new ArrayList<>(fresh);
    }

    static String describeStep(Step s, String userName) {
        String day = s.dayOffset() == 0 ? "Straight away" : s.dayOffset() == 1 ? "After 1 day" : "After " + s.dayOffset() + " days";
        String what = switch (s.action()) {
            case CREATE_TASK -> "add the task \"" + s.text().strip() + "\"";
            case ADD_TAG -> "tag it \"" + s.text().strip().toLowerCase() + "\"";
            case ASSIGN_OWNER -> "make " + (userName == null ? "someone" : userName) + " the owner";
            case NOTIFY_OWNER -> "tell the owner: " + s.text().strip();
        };
        return day + ": " + what;
    }
}
