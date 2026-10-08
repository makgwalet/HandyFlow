package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Who was involved in a visit, worked out from what the system recorded: the nurse-to-doctor handoff moves, who started
 * the visit, who signed it, and the practitioner it was booked with. Pure, so it is tested without a database.
 * The labels say what the person did ("Prepared by"), not their profession, because that is what was recorded.
 */
final class VisitTeamRules {

    /** One handoff move: the status it moved to, who did it, and when. */
    record Step(String toStatus, UUID actor, Instant at) {}

    record Member(String role, String name, Instant at) {}

    private static final Set<String> PREPARING = Set.of("NURSE_IN_PROGRESS", "READY_FOR_DOCTOR");
    private static final Set<String> REVIEWING = Set.of("DOCTOR_REVIEWING", "DOCTOR_COMPLETED");

    private VisitTeamRules() {}

    static List<Member> team(List<Step> steps, UUID startedBy, UUID signedBy, Instant signedAt,
                             String practitionerName, Function<UUID, String> nameOf) {
        List<Member> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean handoff = steps.stream().anyMatch(s -> PREPARING.contains(s.toStatus()) || REVIEWING.contains(s.toStatus()));

        // Without a handoff the visit was done by one person, so "who started it" is the useful fact.
        if (!handoff) add(out, seen, "Started by", name(nameOf, startedBy), null);
        for (Step s : steps) if (PREPARING.contains(s.toStatus())) add(out, seen, "Prepared by", name(nameOf, s.actor()), s.at());
        for (Step s : steps) if (REVIEWING.contains(s.toStatus())) add(out, seen, "Reviewed by", name(nameOf, s.actor()), s.at());
        add(out, seen, "Signed by", name(nameOf, signedBy), signedAt);
        if (practitionerName != null && !practitionerName.isBlank()) add(out, seen, "Seen by", practitionerName.trim(), null);
        return out;
    }

    private static String name(Function<UUID, String> nameOf, UUID id) { return id == null ? null : nameOf.apply(id); }

    private static void add(List<Member> out, Set<String> seen, String role, String name, Instant at) {
        if (name == null || name.isBlank()) return;
        if (seen.add(role + "|" + name)) out.add(new Member(role, name, at));
    }
}
