package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every LEGALPRACTICE_ authority named in the module's controllers and services must be in {@link LpPermission}, and every entry there must be used somewhere.
 * Reads the source tree, so it only runs from the platform folder (as Maven does) and skips elsewhere.
 */
class LpPermissionCatalogueTest {

    private static final Pattern CODE = Pattern.compile("LEGALPRACTICE_[A-Z_]+");

    private static Set<String> used() throws IOException {
        Path root = Path.of("src/main/java/za/co/handyflow/platform/legalpractice");
        assumeTrue(Files.isDirectory(root), "run from the platform folder");
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).filter(p -> !p.getFileName().toString().equals("LpPermission.java")).collect(Collectors.toList())) {
                Matcher m = CODE.matcher(Files.readString(f));
                while (m.find()) out.add(m.group());
            }
        }
        return out;
    }

    @Test
    void everyAuthorityInUseIsInTheCatalogue() throws IOException {
        Set<String> known = java.util.Arrays.stream(LpPermission.values()).map(LpPermission::code).collect(Collectors.toSet());
        Set<String> missing = new TreeSet<>(used());
        missing.removeAll(known);
        assertTrue(missing.isEmpty(), "Add to LpPermission: " + missing);
    }

    @Test
    void everyCatalogueEntryIsUsedSomewhere() throws IOException {
        Set<String> used = used();
        Set<String> unused = new TreeSet<>();
        for (LpPermission p : LpPermission.values()) if (!used.contains(p.code())) unused.add(p.code());
        assertTrue(unused.isEmpty(), "In LpPermission but no controller uses it: " + unused);
    }
}
