package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LpAccessRulesTest {

    private static List<String> codes(LpPermission... p) { return java.util.Arrays.stream(p).map(LpPermission::code).toList(); }
    private static Set<String> found(List<String> codes) { return LpAccessRules.check(codes).stream().map(LpAccessRules.Finding::code).collect(Collectors.toSet()); }

    @Test
    void everyTemplateIsFreeOfWarnings() {
        for (var t : LpRoleTemplates.all().values()) {
            var warnings = LpAccessRules.check(t.permissions().stream().map(LpPermission::code).toList()).stream().filter(f -> f.severity() == LpAccessRules.Severity.WARNING).toList();
            assertTrue(warnings.isEmpty(), t.key() + " has warnings: " + warnings);
        }
    }

    @Test
    void everyTemplateHoldsRealPermissionsAndNoAdmin() {
        assertFalse(LpRoleTemplates.all().isEmpty());
        for (var t : LpRoleTemplates.all().values()) {
            assertFalse(t.permissions().isEmpty(), t.key());
            assertFalse(t.permissions().contains(LpPermission.ADMIN), t.key() + " must not include ADMIN");
            assertFalse(t.name().isBlank()); assertFalse(t.description().isBlank());
        }
    }

    @Test
    void thePartnerApprovesAndTheBookkeeperPrepares() {
        var partner = LpRoleTemplates.all().get("partner").permissions();
        var book = LpRoleTemplates.all().get("bookkeeper").permissions();
        assertTrue(partner.contains(LpPermission.TRUST_PAYMENT_APPROVE)); assertFalse(partner.contains(LpPermission.TRUST_PAYMENT_REQUEST));
        assertTrue(book.contains(LpPermission.TRUST_PAYMENT_REQUEST)); assertFalse(book.contains(LpPermission.TRUST_PAYMENT_APPROVE)); assertFalse(book.contains(LpPermission.TRUST_RECONCILE_APPROVE));
    }

    @Test
    void makerAndCheckerInOneRoleIsAWarning() {
        assertEquals(Set.of("TRUST_PAYMENT"), found(codes(LpPermission.READ, LpPermission.TRUST_PAYMENT_REQUEST, LpPermission.TRUST_PAYMENT_APPROVE)));
        assertTrue(found(codes(LpPermission.READ, LpPermission.TRUST_RECONCILE, LpPermission.TRUST_RECONCILE_APPROVE)).contains("TRUST_RECONCILE"));
        assertTrue(found(codes(LpPermission.READ, LpPermission.KYC_PREPARE, LpPermission.KYC_APPROVE)).contains("KYC"));
        assertTrue(found(codes(LpPermission.READ, LpPermission.WALL_MANAGE, LpPermission.WALL_AUDIT)).contains("WALLS"));
        assertTrue(found(codes(LpPermission.READ, LpPermission.KYC_PREPARE, LpPermission.KYC_APPROVE_SENIOR)).contains("KYC_SENIOR"));
    }

    @Test
    void oneSideAloneIsFine() {
        assertTrue(found(codes(LpPermission.READ, LpPermission.TRUST_PAYMENT_REQUEST)).isEmpty());
        assertTrue(found(codes(LpPermission.READ, LpPermission.TRUST_PAYMENT_APPROVE)).isEmpty());
    }

    @Test
    void adminHoldsEverythingAndSaysSo() {
        var f = LpAccessRules.check(codes(LpPermission.ADMIN));
        var set = f.stream().map(LpAccessRules.Finding::code).collect(Collectors.toSet());
        assertTrue(set.containsAll(Set.of("ADMIN", "TRUST_PAYMENT", "TRUST_RECONCILE", "KYC", "WALLS")));
        assertEquals(LpPermission.values().length, LpAccessRules.effective(codes(LpPermission.ADMIN)).size());
        assertTrue(f.stream().filter(x -> x.code().equals("TRUST_PAYMENT")).allMatch(x -> x.message().contains("through ADMIN")));
    }

    @Test
    void notesForLessSeriousCombinations() {
        var f = LpAccessRules.check(codes(LpPermission.READ, LpPermission.TRUST_RECEIVE, LpPermission.TRUST_PAYMENT_APPROVE, LpPermission.DEADLINE_RULES, LpPermission.DEADLINE_COMPUTE));
        assertTrue(f.stream().allMatch(x -> x.severity() == LpAccessRules.Severity.NOTE));
        assertEquals(Set.of("TRUST_RECEIVE_APPROVE", "DEADLINE_RULES"), f.stream().map(LpAccessRules.Finding::code).collect(Collectors.toSet()));
    }

    @Test
    void missingReadIsNoted() {
        assertTrue(found(codes(LpPermission.TASK_WRITE)).contains("NO_READ"));
        assertFalse(found(codes(LpPermission.READ, LpPermission.TASK_WRITE)).contains("NO_READ"));
        assertFalse(found(codes(LpPermission.REPORT_VIEW)).contains("NO_READ"));
        assertTrue(found(List.of()).isEmpty());
    }

    @Test
    void unknownCodesAreReportedAndOtherModulesIgnored() {
        var f = LpAccessRules.check(List.of("LEGALPRACTICE_READ", "LEGALPRACTICE_TYPO", "HR_ADMIN", "CLINIC_ADMIN"));
        assertEquals(List.of("LEGALPRACTICE_TYPO"), LpAccessRules.unknown(List.of("LEGALPRACTICE_READ", "LEGALPRACTICE_TYPO", "HR_ADMIN")));
        assertEquals(1L, f.stream().filter(x -> x.code().equals("UNKNOWN")).count());
        assertEquals(1, LpAccessRules.effective(java.util.Arrays.asList("LEGALPRACTICE_READ", "HR_ADMIN", "LEGALPRACTICE_TYPO", null)).size());
        assertDoesNotThrow(() -> LpAccessRules.check(null));
    }

    @Test
    void catalogueCodesAreUniqueAndPrefixed() {
        var seen = new java.util.HashSet<String>();
        for (var p : LpPermission.values()) { assertTrue(p.code().startsWith("LEGALPRACTICE_")); assertTrue(seen.add(p.code())); assertFalse(p.description().isBlank()); }
    }
}
