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
 * The built-in JpaRepository lookups (findById, findAllById, getReferenceById, deleteById, existsById, findAll) know nothing about tenants.
 * Fuel code must use the tenant-scoped repository methods instead. A file may use a built-in lookup only when it is listed here with the
 * number of calls that were reviewed and why each is safe; a new call, or a new file, fails this test until it is reviewed.
 */
class FuelUnscopedLookupTest {

    private static final Path ROOT = Path.of("src/main/java/za/co/handyflow/platform/fuel");
    private static final Pattern CALL = Pattern.compile("\\.(findById|getById|getReferenceById|findAllById|deleteById|existsById|findAll)\\(");

    /** file -> {reviewed calls, why safe}. */
    private static final Map<String, Object[]> REVIEWED = Map.ofEntries(
            Map.entry("FuelCostToServeService.java", new Object[]{1, "ids come from the tenant's own deliveries; each row is then checked against the tenant"}),
            Map.entry("FuelEligibilityService.java", new Object[]{2, "changeover rule is checked against the tenant; tanker ids come from the tenant's own override rows"}),
            Map.entry("FuelFacadeImpl.java", new Object[]{1, "tank ids come from the tenant's own dispatches; only names are read"}),
            Map.entry("FuelGroupReportService.java", new Object[]{1, "each tank is checked against the tenant"}),
            Map.entry("FuelImportAutoAcceptService.java", new Object[]{2, "import id comes from a result just saved for the tenant; the tenant is then read from the row itself"}),
            Map.entry("FuelPortalDataService.java", new Object[]{1, "portal identity is global; the id is the signed-in portal user's own"}),
            Map.entry("FuelPosImportService.java", new Object[]{1, "the two-argument findById is the tenant-scoped one"}),
            Map.entry("FuelRequisitionService.java", new Object[]{1, "the order is checked against the tenant"}),
            Map.entry("FuelRouteCostAllocationService.java", new Object[]{1, "each site is checked against the tenant"}),
            Map.entry("FuelRouteCostService.java", new Object[]{1, "each site is checked against the tenant"}),
            Map.entry("FuelRouteOptimiserService.java", new Object[]{1, "each site is checked against the tenant"}),
            Map.entry("FuelStayRequestService.java", new Object[]{1, "portal identity is global; the id is the signed-in portal user's own"}),
            Map.entry("FuelToleranceService.java", new Object[]{4, "the tenant id is the key of the policy table"}));

    @Test
    void builtInLookupsAreReviewed() throws IOException {
        List<String> problems = new ArrayList<>();
        int filesScanned = 0;
        for (String dir : List.of("application", "api")) {
            try (Stream<Path> s = Files.walk(ROOT.resolve(dir))) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                    filesScanned++;
                    String name = p.getFileName().toString();
                    Matcher m = CALL.matcher(Files.readString(p));
                    int n = 0;
                    while (m.find()) n++;
                    if (n == 0) continue;
                    Object[] ok = REVIEWED.get(name);
                    if (ok == null) problems.add(name + " makes " + n + " unscoped lookup(s) and is not reviewed");
                    else if (n > (Integer) ok[0]) problems.add(name + " now makes " + n + " unscoped lookups; " + ok[0] + " were reviewed");
                }
            }
        }
        assertThat(filesScanned).isGreaterThan(50);
        assertThat(problems).as("unscoped repository lookups").isEmpty();
    }
}
