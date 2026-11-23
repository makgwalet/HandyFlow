package za.co.handyflow.platform.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two Spring beans (or two JPA entities) with the same default name stop the whole application from starting, and Spring reports only one
 * such pair per run. This reads the source and names every clash that involves the Security module, so it shows up in the test run
 * instead of one startup at a time. Fix: give one of them an explicit name, e.g. {@code @Service("securityIncidentService")}.
 */
class BeanNamesTest {

    private static final Pattern STEREOTYPE = Pattern.compile("(?m)^\\s*@(?:[a-z][\\w]*\\.)*(Service|Component|RestController|Controller|Repository|Configuration|Entity)\\b(\\(([^)]*)\\))?");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern ENTITY_NAME = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern DATA_REPO = Pattern.compile("interface\\s+\\w+(<[^>]*>)?\\s+extends\\s+[\\w.]*Repository\\b");

    record Found(String key, String where) {}

    static Path root() {
        for (String r : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(r, "za/co/handyflow/platform");
            if (Files.isDirectory(p)) return p;
        }
        fail("no source directory"); return null;
    }

    static List<Found> scan(Path base) throws IOException {
        List<Found> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                String s = Files.readString(f);
                String cls = f.getFileName().toString().replace(".java", "");
                String rel = base.relativize(f).toString().replace('\\', '/');
                Matcher m = STEREOTYPE.matcher(s);
                if (m.find()) {
                    String kind = m.group(1), arg = m.group(3) == null ? "" : m.group(3);
                    if (kind.equals("Entity")) {
                        Matcher n = ENTITY_NAME.matcher(arg);
                        out.add(new Found("entity:" + (n.find() ? n.group(1) : cls), rel));
                    } else {
                        Matcher n = QUOTED.matcher(arg);
                        out.add(new Found("bean:" + (n.find() ? n.group(1) : decap(cls)), rel));
                    }
                } else if (DATA_REPO.matcher(s).find()) {
                    out.add(new Found("bean:" + decap(cls), rel));
                }
            }
        }
        return out;
    }

    static String decap(String s) { return Character.toLowerCase(s.charAt(0)) + s.substring(1); }

    /** Every key used by two or more classes where at least one is in the given module. */
    static Map<String, List<String>> clashes(List<Found> found, String module) {
        Map<String, List<String>> by = new TreeMap<>();
        for (Found f : found) by.computeIfAbsent(f.key(), k -> new ArrayList<>()).add(f.where());
        return by.entrySet().stream()
                .filter(e -> e.getValue().size() > 1 && e.getValue().stream().anyMatch(w -> w.startsWith(module + "/")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
    }

    @Test
    void noSecurityBeanOrEntityNameIsUsedTwice() throws Exception {
        Map<String, List<String>> c = clashes(scan(root()), "security");
        assertTrue(c.isEmpty(), "Names used twice (give one an explicit name): " + c);
    }

    // ---- the checker itself ----

    @Test
    void theCheckerSeesAClashAndAnExplicitNameClearsIt() throws Exception {
        Path dir = Files.createTempDirectory("beans");
        write(dir, "security/a/Thing.java", "@Service\npublic class Thing {}");
        write(dir, "other/a/Thing.java", "@Service\npublic class Thing {}");
        assertEquals(List.of("bean:thing"), new ArrayList<>(clashes(scan(dir), "security").keySet()));
        write(dir, "other/a/Thing.java", "@Service(\"otherThing\")\npublic class Thing {}");
        assertTrue(clashes(scan(dir), "security").isEmpty());
    }

    @Test
    void entitiesAndDataRepositoriesAreCheckedToo() throws Exception {
        Path dir = Files.createTempDirectory("beans");
        write(dir, "security/m/Incident.java", "@Entity\n@Table(name = \"a\")\npublic class Incident {}");
        write(dir, "other/m/Incident.java", "@Entity\n@Table(name = \"b\")\npublic class Incident {}");
        write(dir, "security/r/IncidentRepository.java", "public interface IncidentRepository extends JpaRepository<Incident, UUID> {}");
        write(dir, "other/r/IncidentRepository.java", "public interface IncidentRepository extends JpaRepository<Incident, UUID> {}");
        assertEquals(List.of("bean:incidentRepository", "entity:Incident"), new ArrayList<>(clashes(scan(dir), "security").keySet()));
        write(dir, "other/m/Incident.java", "@Entity(name = \"OtherIncident\")\npublic class Incident {}");
        assertEquals(List.of("bean:incidentRepository"), new ArrayList<>(clashes(scan(dir), "security").keySet()));
    }

    @Test
    void aFullyQualifiedAnnotationWithAnExplicitNameCountsAsNamed() throws Exception {
        Path dir = Files.createTempDirectory("beans");
        write(dir, "security/r/IncidentRepository.java", "public interface IncidentRepository extends JpaRepository<Incident, UUID> {}");
        write(dir, "other/r/IncidentRepository.java", "@org.springframework.stereotype.Repository(\"otherIncidentRepository\")\npublic interface IncidentRepository extends JpaRepository<Incident, UUID> {}");
        assertTrue(clashes(scan(dir), "security").isEmpty());
        write(dir, "other/r/IncidentRepository.java", "@org.springframework.stereotype.Repository\npublic interface IncidentRepository extends JpaRepository<Incident, UUID> {}");
        assertEquals(List.of("bean:incidentRepository"), new ArrayList<>(clashes(scan(dir), "security").keySet()));
    }

    @Test
    void aClashBetweenTwoOtherModulesIsNotThisModulesToFix() throws Exception {
        Path dir = Files.createTempDirectory("beans");
        write(dir, "a/x/Thing.java", "@Service\npublic class Thing {}");
        write(dir, "b/x/Thing.java", "@Service\npublic class Thing {}");
        assertTrue(clashes(scan(dir), "security").isEmpty());
    }

    private static void write(Path dir, String rel, String body) throws IOException {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, body);
    }
}
