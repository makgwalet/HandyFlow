package za.co.handyflow.platform.security.application.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Narrows the records behind the monthly summary to the sites a user may see. Pure. */
final class MonthlySummaryScope {
    private MonthlySummaryScope() {}

    /**
     * `allowed` null = the whole company: the list is returned as it is. Otherwise only records at one of those sites are kept;
     * a record with no site (an incident that was not tied to a site) is not at any of them, so it is left out.
     */
    static <T> List<T> keep(List<T> records, Function<T, UUID> siteOf, Set<UUID> allowed) {
        if (allowed == null) return records;
        return records.stream().filter(r -> {
            UUID s = siteOf.apply(r);
            return s != null && allowed.contains(s);
        }).toList();
    }
}
