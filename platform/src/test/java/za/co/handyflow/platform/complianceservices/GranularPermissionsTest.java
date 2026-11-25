package za.co.handyflow.platform.complianceservices;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The narrower Compliance Services write permissions (V907) sit alongside COMPLIANCE_SERVICES_MANAGE: every guard that names one of them must still
 * accept MANAGE and ADMIN, so no existing role loses access, and every new permission must actually be used by a controller. Scans source, no database.
 */
class GranularPermissionsTest {

    private static final Path MAIN = Path.of("src/main");
    private static final Pattern GRANULAR = Pattern.compile("COMPLIANCE_SERVICES_[A-Z]+_MANAGE");
    private static final Pattern GUARD = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");

    private static String source(Path dir, String suffix) throws IOException {
        StringBuilder b = new StringBuilder();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(suffix)).toList()) b.append(Files.readString(f)).append('\n');
        }
        return b.toString();
    }

    @Test
    void everyNewPermissionIsUsedByAControllerAndKeepsManageAndAdmin() throws IOException {
        String migration = Files.readString(MAIN.resolve("resources/db/migration/V907__complianceservices_granular_manage_permissions.sql"));
        Set<String> declared = new TreeSet<>();
        Matcher m = Pattern.compile("\\(gen_random_uuid\\(\\), '(COMPLIANCE_SERVICES_[A-Z]+_MANAGE)'").matcher(migration);
        while (m.find()) declared.add(m.group(1));
        assertTrue(declared.size() >= 6, "expected the granular permissions in V907");

        String controllers = source(MAIN.resolve("java/za/co/handyflow/platform/complianceservices/api"), ".java");
        Set<String> used = new TreeSet<>();
        Matcher g = GUARD.matcher(controllers);
        while (g.find()) {
            String args = g.group(1);
            Matcher n = GRANULAR.matcher(args);
            boolean granular = false;
            while (n.find()) { used.add(n.group()); granular = true; }
            if (granular) {
                assertTrue(args.contains("'COMPLIANCE_SERVICES_MANAGE'") && args.contains("'COMPLIANCE_SERVICES_ADMIN'"), "a guard with a narrow permission must still accept MANAGE and ADMIN: " + args);
            }
        }
        assertEquals(declared, used, "every permission added in V907 should guard something, and nothing else should be invented");
    }
}
