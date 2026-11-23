package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Branches, the scan log and the site overview are limited to the user's sites. Read from the source, like the other scope tests. */
class SiteScopeStaffEndpointsTest {

    private static String src(String f) throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security/api", f);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + f); return "";
    }

    /** The text of one method, from its signature to the next method-level annotation or the end of the class. */
    private static String method(String s, String signature) {
        int a = s.indexOf(signature);
        assertTrue(a >= 0, "no method " + signature);
        int next = s.indexOf("\n    @", a + signature.length());
        return s.substring(a, next < 0 ? s.length() : next);
    }

    private static void guardedBefore(String body, String guard, String service) {
        int g = body.indexOf(guard), c = body.indexOf(service);
        assertTrue(g >= 0, "missing " + guard);
        assertTrue(c >= 0, "missing " + service);
        assertTrue(g < c, guard + " must run before " + service);
    }

    @Test
    void everyBranchChangeNeedsTheWholeCompany() throws Exception {
        String s = src("BranchController.java");
        guardedBefore(method(s, "createBranch("), "wholeCompany();", "branchService.createBranch");
        guardedBefore(method(s, "updateBranch("), "wholeCompany();", "branchService.updateBranch");
        guardedBefore(method(s, "deactivateBranch("), "wholeCompany();", "branchService.deactivateBranch");
    }

    @Test
    void readingBranchesStaysOpen() throws Exception {
        String s = src("BranchController.java");
        assertFalse(method(s, "listBranches(").contains("wholeCompany"));
        assertFalse(method(s, "getBranch(").contains("wholeCompany"));
    }

    @Test
    void branchGuardIsTheUnrestrictedCheck() throws Exception {
        String s = src("BranchController.java");
        assertTrue(s.contains("SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());"));
        assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)\n    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;"));
    }

    @Test
    void scanLogChecksTheShiftFirst() throws Exception {
        String s = src("ScanLogController.java");
        guardedBefore(method(s, "getScans("), "shift(id);", "scanLogService.getScansForShift");
        assertTrue(s.contains("SiteScopeService.checkChild(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.SHIFT, id);"));
    }

    @Test
    void siteOverviewChecksTheSiteFirst() throws Exception {
        String s = src("SiteOverviewController.java");
        guardedBefore(method(s, "overview("), "site(id);", "overviewService.overview");
        assertTrue(s.contains("SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);"));
    }

    @Test
    void featureGuardStillRunsFirst() throws Exception {
        guardedBefore(method(src("ScanLogController.java"), "getScans("), "featureGuard.requireModule(\"security\")", "shift(id);");
        guardedBefore(method(src("SiteOverviewController.java"), "overview("), "featureGuard.requireModule(\"security\")", "site(id);");
    }
}
