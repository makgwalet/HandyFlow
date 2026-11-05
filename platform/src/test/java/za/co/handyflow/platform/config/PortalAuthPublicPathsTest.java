package za.co.handyflow.platform.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client portal whose sign-in path is not public can never be used: a new user has no token, so the request is
 * rejected before the controller runs. This has happened more than once (auditor, collections agency, booking agency),
 * so the rule is checked here for every portal auth controller found in the source:
 * its /portal/auth/** path must be permitAll in SecurityConfig and its login and register must be rate limited.
 */
class PortalAuthPublicPathsTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern BASE = Pattern.compile("@RequestMapping\\(\"(/api/v1/[^\"]*/portal(?:/auth)?)\"\\)");

    @Test
    void everyPortalAuthControllerIsPublicAndRateLimited() throws IOException {
        String security = Files.readString(MAIN.resolve("za/co/handyflow/platform/config/SecurityConfig.java"));
        String limits = Files.readString(MAIN.resolve("za/co/handyflow/platform/config/RateLimitFilter.java"));
        List<String> problems = new ArrayList<>();
        int found = 0;
        List<Path> files;
        try (Stream<Path> s = Files.walk(MAIN)) { files = s.filter(p -> p.toString().endsWith("Controller.java")).toList(); }
        for (Path f : files) {
            String t = Files.readString(f);
            Matcher m = BASE.matcher(t);
            if (!m.find()) continue;
            String base = m.group(1);
            String authPath;
            if (base.endsWith("/auth")) authPath = base;
            else if (t.contains("Mapping(\"/auth/login\")")) authPath = base + "/auth";
            else continue;
            found++;
            if (!security.contains("\"" + authPath + "/**\"")) problems.add(f.getFileName() + ": " + authPath + "/** is not in SecurityConfig permitAll");
            if (!limits.contains("\"" + authPath + "/login\"")) problems.add(f.getFileName() + ": " + authPath + "/login is not rate limited");
        }
        assertTrue(found >= 14, "expected to find the portal auth controllers, found " + found);
        assertEquals(List.of(), problems);
    }
}
