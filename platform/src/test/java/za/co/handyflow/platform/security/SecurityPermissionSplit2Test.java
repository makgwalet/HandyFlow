package za.co.handyflow.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

/** Source-scanning checks that the sensitive actions demand their own permission. Needs no database. */
class SecurityPermissionSplit2Test {

    private static final Path API = Path.of("src/main/java/za/co/handyflow/platform/security/api");

    /** The authority on the first @PreAuthorize that follows the mapping annotation for the given method and path. */
    private static String authorityFor(String file, String httpMethod, String path) throws IOException {
        List<String> lines = Files.readAllLines(API.resolve(file));
        Pattern mapping = Pattern.compile("@" + httpMethod + "Mapping\\(\\s*\"" + Pattern.quote(path) + "\"");
        for (int i = 0; i < lines.size(); i++) {
            if (!mapping.matcher(lines.get(i)).find()) continue;
            for (int j = i; j < Math.min(i + 6, lines.size()); j++) {
                Matcher a = Pattern.compile("@PreAuthorize\\(\"([^\"]*)\"\\)").matcher(lines.get(j));
                if (a.find()) return a.group(1);
            }
        }
        fail("no @PreAuthorize found for " + httpMethod + " " + path + " in " + file);
        return null;
    }

    @Test void firearmIssueAndReturnNeedArmouryIssue() throws IOException {
        assertEquals("hasAuthority('SECURITY_ARMOURY_ISSUE')", authorityFor("ArmouryController.java", "Post", "/{id}/issue"));
        assertEquals("hasAuthority('SECURITY_ARMOURY_ISSUE')", authorityFor("ArmouryController.java", "Post", "/{id}/return"));
    }

    @Test void bankDetailsNeedBankingPermission() throws IOException {
        assertEquals("hasAuthority('SECURITY_GUARD_BANKING_MANAGE')", authorityFor("GuardController.java", "Post", "/{id}/bank-details"));
    }

    @Test void screeningDecisionNeedsDecidePermission() throws IOException {
        assertEquals("hasAuthority('SECURITY_SCREENING_DECIDE')", authorityFor("GuardScreeningController.java", "Post", "/{screeningId}/decision"));
    }

    @Test void otherArmouryWritesAreUnchanged() throws IOException {
        assertEquals("hasAuthority('SECURITY_MANAGE')", authorityFor("ArmouryController.java", "Post", "/{id}/service"));
        assertEquals("hasAuthority('SECURITY_ADMIN')", authorityFor("ArmouryController.java", "Post", "/{id}/report-lost"));
    }
}
