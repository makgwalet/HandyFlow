package za.co.handyflow.platform.fuel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * H01 - every @Query in the Fuel repositories must filter by tenant, unless the method is on a
 * reviewed allow-list with a reason. Adding to the allow-list requires a code review.
 */
class FuelRepositoryTenantScopingTest {

    private static final Path REPOS = Path.of("src/main/java/za/co/handyflow/platform/fuel/domain/repository");

    /** "Repository.method" -> why it is intentionally not tenant-filtered. */
    private static final Map<String, String> ALLOWED = Map.of(
            "FuelComplianceItemRepository.findExpiringBetweenAllTenants", "scheduler sweeps all tenants",
            "FuelComplianceReminderRepository.findByItemIds", "item ids come from the scheduler sweep",
            "FuelDeliveryRepository.findUpcomingNeedingReminder", "scheduler sweeps all tenants",
            "FuelDeliveryRepository.findOverdueNeedingAlert", "scheduler sweeps all tenants",
            "FuelDispatchRepository.sumLitresDispensedByTankIdsRaw", "tank ids come from a tenant-scoped tank list",
            "FuelPortalAccessGrantRepository.findActiveGrantsForUser", "portal identity is global; tenant derived from grant",
            "FuelPortalAccessGrantRepository.findActiveGrant", "portal identity is global; tenant derived from grant");

    private static final Pattern QUERY = Pattern.compile("@Query\\((.*?)\\)\\s*\\n\\s*[^\\n]*?\\s(\\w+)\\(", Pattern.DOTALL);

    @Test
    void everyQueryIsTenantScopedOrAllowListed() throws IOException {
        List<String> unscoped = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> s = Files.list(REPOS)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                String repo = p.getFileName().toString().replace(".java", "");
                Matcher m = QUERY.matcher(Files.readString(p));
                while (m.find()) {
                    seen++;
                    String q = m.group(1);
                    boolean scoped = q.contains("tenantId") || q.contains("tenant_id");
                    String key = repo + "." + m.group(2);
                    if (!scoped && !ALLOWED.containsKey(key)) unscoped.add(key);
                }
            }
        }
        assertThat(seen).isGreaterThan(50);
        assertThat(unscoped).as("queries with no tenant filter").isEmpty();
    }

    @Test
    void allowListHasNoStaleEntries() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> s = Files.list(REPOS)) {
            for (Path p : s.toList()) all.append(p.getFileName()).append('\n').append(Files.readString(p));
        }
        for (String key : ALLOWED.keySet()) {
            assertThat(all.toString()).as("stale allow-list entry " + key).contains(key.substring(key.indexOf('.') + 1) + "(");
        }
    }
}
