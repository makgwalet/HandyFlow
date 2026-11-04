package za.co.handyflow.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IAM Phase 0.8. Reads the controller sources and checks two things:
 * every controller has {@code @PreAuthorize} unless it is on the reviewed public list, and every controller
 * checks module entitlement unless it is on the known-debt list (which may only shrink).
 * Source-text based on purpose: it needs no Spring context and cannot be skipped by a wrong profile.
 */
class ControllerAccessCoverageTest {

    private static final Path SRC = Path.of("src/main/java");
    private static final String RES = "/security/";

    private static Set<String> controllersWhere(java.util.function.Predicate<String> offends) throws IOException {
        try (Stream<Path> files = Files.walk(SRC)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String src = read(p);
                        return src.contains("@RestController") && offends.test(src);
                    })
                    .map(p -> SRC.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static String read(Path p) {
        try { return Files.readString(p); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static Set<String> list(String name) throws IOException {
        try (var in = ControllerAccessCoverageTest.class.getResourceAsStream(RES + name)) {
            assertThat(in).as("test resource " + name).isNotNull();
            return new String(in.readAllBytes()).lines().map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#")).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    @Test
    @DisplayName("every controller has @PreAuthorize unless it is on the reviewed public list")
    void everyControllerIsAuthorised() throws IOException {
        Set<String> actual = controllersWhere(s -> !s.contains("@PreAuthorize"));
        Set<String> allowed = list("controllers-without-preauthorize.txt");

        Set<String> newOffenders = new TreeSet<>(actual);
        newOffenders.removeAll(allowed);
        assertThat(newOffenders)
                .as("controllers with no @PreAuthorize that are not on the reviewed public list")
                .isEmpty();

        Set<String> stale = new TreeSet<>(allowed);
        stale.removeAll(actual);
        assertThat(stale).as("allow-list entries that now have @PreAuthorize or no longer exist (remove them)").isEmpty();
    }

    @Test
    @DisplayName("every controller checks module entitlement unless it is on the known-debt list")
    void everyControllerChecksModuleEntitlement() throws IOException {
        Set<String> actual = controllersWhere(s -> !(s.contains("FeatureGuard")
                || s.contains("requireModule") || s.contains("RequiresModule")));
        Set<String> debt = list("controllers-without-module-guard.txt");

        Set<String> newOffenders = new TreeSet<>(actual);
        newOffenders.removeAll(debt);
        assertThat(newOffenders)
                .as("NEW controllers that do not check module entitlement (call FeatureGuard.requireModule)")
                .isEmpty();

        Set<String> fixed = new TreeSet<>(debt);
        fixed.removeAll(actual);
        assertThat(fixed)
                .as("controllers on the debt list that now check entitlement (delete them from the list)")
                .isEmpty();
    }
}
