package za.co.handyflow.platform;

// UNVERIFIED AGAINST YOUR REAL TEST SETUP — I have not seen any of your
// actual test source files, your test base class, or how you wire the
// DataSource/JdbcTemplate in tests (real local Postgres vs Testcontainers
// vs something else). This uses only Spring primitives that are certainly
// already on your classpath (no new dependency added). Please adapt the
// @SpringBootTest / JdbcTemplate wiring below to match whatever your
// existing tests already do — paste one of your real test files and I'll
// rewrite this to match exactly, the same way the POS settings panel was
// rewritten once the real frontend conventions were confirmed.

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Scans every @RestController in the codebase for @PreAuthorize annotations
 * (class-level and method-level), extracts every permission string
 * referenced inside hasAuthority(...) / hasAnyAuthority(...) calls, and
 * asserts each one actually exists in the `permissions` table.
 * <p>
 * WHY this exists: nothing previously verified that a permission string
 * checked in @PreAuthorize and a permission string seeded into the
 * database were the same string. Confirmed live tonight — a
 * PAYROLLBUREAU_* permission mismatch produced a working login with the
 * right-looking permissions in the JWT, but a 403 on every actual call,
 * and nothing in the build or startup caught it. This test exists to make
 * that specific failure mode impossible to ship silently again.
 * <p>
 * SCOPE: this only catches "checked but never granted" (a controller
 * requires a permission that doesn't exist in the DB — exactly tonight's
 * bug). It deliberately does NOT flag "granted but never checked" (a
 * permission exists in the DB but no controller ever requires it) as a
 * failure — that's a much noisier, lower-value signal (plenty of
 * permissions are legitimately unused by design, e.g. reserved for a
 * future endpoint), so it's only logged, not asserted.
 */
@SpringBootTest
public class PermissionConsistencyTest {

    // Matches single-quoted, ALL-CAPS-with-underscores literals — the
    // permission-naming convention confirmed against real code tonight
    // (USER_READ, POS_MANAGE, PAYROLLBUREAU_ADMIN, etc.). Deliberately
    // excludes lowercase/mixed-case quoted strings so this doesn't false-
    // positive on other SpEL literals that might appear in a more complex
    // @PreAuthorize expression (e.g. a role name, a comparison value).
    private static final Pattern PERMISSION_LITERAL = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    private static final String BASE_PACKAGE = "za.co.handyflow.platform";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyPreAuthorizePermissionExistsInDatabase() throws ClassNotFoundException {
        // key = permission string, value = "ClassName#methodName" (or "ClassName [class-level]")
        // it was first found on — kept for a readable failure message.
        Map<String, String> referencedPermissions = new LinkedHashMap<>();

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controllerClass = Class.forName(candidate.getBeanClassName());

            PreAuthorize classLevel = controllerClass.getAnnotation(PreAuthorize.class);
            if (classLevel != null) {
                extractPermissions(classLevel.value())
                        .forEach(p -> referencedPermissions.putIfAbsent(
                                p, controllerClass.getSimpleName() + " [class-level]"));
            }

            for (Method method : controllerClass.getDeclaredMethods()) {
                PreAuthorize methodLevel = method.getAnnotation(PreAuthorize.class);
                if (methodLevel == null) continue;
                extractPermissions(methodLevel.value())
                        .forEach(p -> referencedPermissions.putIfAbsent(
                                p, controllerClass.getSimpleName() + "#" + method.getName()));
            }
        }

        Set<String> seededPermissions = new HashSet<>(
                jdbc.queryForList("SELECT name FROM permissions", String.class));

        Set<String> missing = new TreeSet<>();
        for (String permission : referencedPermissions.keySet()) {
            if (!seededPermissions.contains(permission)) {
                missing.add(permission);
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder msg = new StringBuilder(
                    "Found " + missing.size() + " permission(s) checked in @PreAuthorize " +
                            "but never seeded into the `permissions` table:\n");
            for (String permission : missing) {
                msg.append("  - ").append(permission)
                        .append("  (first referenced in ")
                        .append(referencedPermissions.get(permission)).append(")\n");
            }
            msg.append("Either the controller has a typo, or the seeding migration is missing/wrong. ")
                    .append("This is exactly the class of bug that produced a working login with a ")
                    .append("correct-looking JWT but a 403 on every real call.");
            fail(msg.toString());
        }
    }

    private Set<String> extractPermissions(String preAuthorizeExpression) {
        Set<String> found = new HashSet<>();
        Matcher matcher = PERMISSION_LITERAL.matcher(preAuthorizeExpression);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}