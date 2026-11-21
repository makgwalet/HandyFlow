package za.co.handyflow.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every Security scheduled job must (1) run through {@code SecurityJobRunner.exclusively} so two app instances never
 * run it at once, and (2) say which time zone its cron is in. Scans source files; needs no database.
 */
class SecuritySchedulersCoverageTest {

    private static final Path SECURITY = Path.of("src/main/java/za/co/handyflow/platform/security");
    private static final Pattern SCHEDULED = Pattern.compile("^\\s*@Scheduled\\((.*)\\)\\s*(//.*)?$");

    private record Job(Path file, String args) {}

    private static List<Job> jobs() throws IOException {
        List<Job> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SECURITY)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(f)) {
                    Matcher m = SCHEDULED.matcher(line);
                    if (m.find()) out.add(new Job(f, m.group(1)));
                }
            }
        }
        return out;
    }

    @Test void thereAreScheduledJobsToCheck() throws IOException {
        assertTrue(jobs().size() >= 10, "expected the Security scheduled jobs, found " + jobs().size());
    }

    @Test void everyJobRunsExclusively() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Job j : jobs()) if (!Files.readString(j.file()).contains("SecurityJobRunner.exclusively(")) bad.add(j.file().getFileName().toString());
        assertEquals(List.of(), bad, "scheduled jobs that do not use SecurityJobRunner.exclusively");
    }

    @Test void everyCronNamesItsTimeZone() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Job j : jobs()) if (j.args().contains("cron") && !j.args().contains("zone")) bad.add(j.file().getFileName() + ": " + j.args());
        assertEquals(List.of(), bad, "cron jobs without a zone");
    }

    @Test void nobodyUsesTheServerClockForToday() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Job j : jobs()) {
            String src = Files.readString(j.file());
            if (src.contains("LocalDate.now()")) bad.add(j.file().getFileName().toString());
        }
        assertEquals(List.of(), bad, "scheduled classes that call LocalDate.now() without a zone");
    }
}
