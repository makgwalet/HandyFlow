package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production runs everything in db/migration, so demo tenants and demo journals must live in db/seed only.
 * Run with the platform folder as the working directory.
 */
class NoDemoDataInMigrationsTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path SEED = Path.of("src/main/resources/db/seed");

    /** Ids of the demo tenants (Zeta Earthmoving, Acme Security, Beta Construction) and the demo guard id prefix. */
    private static final List<String> DEMO_MARKERS = List.of(
            "9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f",
            "f3ca02b3-eca5-4035-8756-941c72ab6512",
            "aeb2b97f-9523-4bd7-a060-685a10c24831",
            "a1000000-");

    private static List<Path> sqlFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
        }
    }

    @Test void noMigrationCreatesOrFillsADemoTenant() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path p : sqlFiles(MIGRATIONS)) {
            String sql = Files.readString(p);
            for (String marker : DEMO_MARKERS) {
                if (sql.contains(marker)) offenders.add(p.getFileName() + " contains " + marker);
            }
        }
        assertTrue(offenders.isEmpty(), "Move these to db/seed: " + offenders);
    }

    @Test void noVersionExistsInBothFolders() throws IOException {
        List<String> clashes = new ArrayList<>();
        for (Path s : sqlFiles(SEED)) {
            String name = s.getFileName().toString();
            if (Files.exists(MIGRATIONS.resolve(name))) clashes.add(name);
            String version = name.substring(0, name.indexOf("__"));
            for (Path m : sqlFiles(MIGRATIONS)) {
                if (m.getFileName().toString().startsWith(version + "__")) clashes.add(version);
            }
        }
        assertTrue(clashes.isEmpty(), "The seed profile would fail on duplicate versions: " + clashes);
    }

    @Test void theSeedFolderStillHoldsTheDemoData() throws IOException {
        assertTrue(Files.exists(SEED.resolve("V105_1__zeta_demo_prerequisites.sql")));
        assertTrue(Files.exists(SEED.resolve("V107__accounting_test_data_zeta.sql")));
        assertTrue(Files.exists(SEED.resolve("V108__accounting_jan_feb_may_jun_zeta.sql")));
    }
}
