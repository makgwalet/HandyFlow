package za.co.handyflow.platform.facilities;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against a bug that no unit test with fakes can see: these entities hold {@code @Embedded TenantId tenantId},
 * and Hibernate refuses to bind a raw UUID (what {@code :#{#tenantId.value}} produces) to a comparison against the
 * embeddable itself ("Argument [UUID] did not match parameter type [TenantId]"), so every such query fails at run time
 * with a 500. The predicate must be {@code x.tenantId.value = :#{#tenantId.value}}.
 * <p>
 * FmPortalAccessGrant is the one entity whose tenantId is a plain UUID column, so its repository is exempt.
 */
class FacilityRepositoryTenantPredicateTest {

    private static final Pattern BAD = Pattern.compile("\\btenantId = :#\\{#tenantId\\.value}");
    private static final List<String> EXEMPT = List.of("FmPortalAccessGrantRepository.java");

    @Test
    void repositoriesCompareTheEmbeddedTenantIdByItsValue() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : List.of("facilities", "facilitiesmanagement")) {
            Path dir = Path.of("src/main/java/za/co/handyflow/platform", module, "domain/repository");
            if (!Files.isDirectory(dir)) continue; // run from an unexpected working directory: nothing to check
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith("Repository.java")).toList()) {
                    if (EXEMPT.contains(f.getFileName().toString())) continue;
                    String[] lines = Files.readString(f).split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        if (BAD.matcher(lines[i]).find()) offenders.add(f.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "tenantId must be compared as tenantId.value in: " + offenders);
    }
}
