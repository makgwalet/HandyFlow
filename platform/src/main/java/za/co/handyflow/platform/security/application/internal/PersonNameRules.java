package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Turning user ids into the names people recognise. Pure. */
final class PersonNameRules {
    private PersonNameRules() {}

    /** More ids than this in one look-up are cut off; a draft names at most three people. */
    static final int MAX_IDS = 20;

    /** "First Last"; the email address when the name is empty; null when there is neither. */
    static String display(String first, String last, String email) {
        String name = (first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim());
        name = name.trim().replaceAll("\\s+", " ");
        if (!name.isEmpty()) return name;
        return email == null || email.isBlank() ? null : email.trim();
    }

    /** The ids to look up: no nulls, no repeats, in the order given, at most MAX_IDS. */
    static List<UUID> distinct(Collection<UUID> ids) {
        if (ids == null) return List.of();
        LinkedHashSet<UUID> seen = new LinkedHashSet<>();
        for (UUID id : ids) if (id != null) seen.add(id);
        List<UUID> out = new ArrayList<>(seen);
        return out.size() > MAX_IDS ? List.copyOf(out.subList(0, MAX_IDS)) : List.copyOf(out);
    }
}
