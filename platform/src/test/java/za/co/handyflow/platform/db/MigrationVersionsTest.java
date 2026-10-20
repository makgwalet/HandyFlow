package za.co.handyflow.platform.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against the Flyway mistakes that stop the whole application starting: two files with the same version, a version that
 * cannot be read, and a file with nothing in it. Runs on the files in the source tree, so it fails on a pull request, not on deploy.
 */
class MigrationVersionsTest {
    static final Path DIR = Path.of("src/main/resources/db/migration");
    static final Pattern NAME = Pattern.compile("^V(\\d+(?:_\\d+)*)__([A-Za-z0-9_]+)\\.sql$");

    static List<Path> files() throws IOException {
        if (!Files.isDirectory(DIR)) return List.of();
        try (Stream<Path> s = Files.list(DIR)) { return s.filter(p -> p.toString().endsWith(".sql")).sorted().toList(); }
    }

    @Test void everyMigrationHasAReadableFlywayName() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : files()) if (!NAME.matcher(p.getFileName().toString()).matches()) bad.add(p.getFileName().toString());
        assertTrue(bad.isEmpty(), "Names Flyway cannot read: " + bad);
    }

    @Test void noTwoMigrationsShareAVersion() throws IOException {
        Map<String, List<String>> byVersion = new TreeMap<>();
        for (Path p : files()) {
            Matcher m = NAME.matcher(p.getFileName().toString());
            if (m.matches()) byVersion.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(p.getFileName().toString());
        }
        Map<String, List<String>> dup = new TreeMap<>();
        byVersion.forEach((v, f) -> { if (f.size() > 1) dup.put(v, f); });
        assertTrue(dup.isEmpty(), "Same version used twice (Flyway refuses to start): " + dup);
    }

    @Test void noMigrationIsEmpty() throws IOException {
        List<String> empty = new ArrayList<>();
        for (Path p : files()) {
            String body = Files.readString(p).replaceAll("(?m)^\\s*--.*$", "").trim();
            if (body.isEmpty()) empty.add(p.getFileName().toString());
        }
        assertTrue(empty.isEmpty(), "Migrations with no statements (the tables they were meant to create will be missing): " + empty);
    }

    @Test void theFleetAssetTablesTheEntitiesMapAreCreated() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path p : files()) all.append(Files.readString(p)).append('\n');
        String sql = all.toString().toLowerCase(Locale.ROOT);
        for (String table : List.of("fleet_asset_profiles", "fleet_asset_attachments", "fleet_attachment_assignments", "fleet_asset_meter_readings", "fleet_accidents")) {
            assertTrue(sql.contains("create table if not exists " + table) || sql.contains("create table " + table), "No migration creates " + table);
        }
    }
}
