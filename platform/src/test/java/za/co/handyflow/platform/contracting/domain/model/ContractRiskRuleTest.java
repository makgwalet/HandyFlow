package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractRiskRuleTest {

    LocalDate today = LocalDate.of(2026, 11, 18);

    ContractRiskRule draft() {
        return ContractRiskRule.create(UUID.randomUUID(), "Needs end date", null, "REQUIRES_END_DATE", null, null, "service, lease", "medium", true,
                "No end date", "Add one", "Contract law", null, UUID.randomUUID());
    }

    ContractRiskRule published(LocalDate next) {
        ContractRiskRule r = draft();
        r.approve("A Attorney", "Admitted attorney", today.minusDays(1), next, null, today);
        r.publish(today);
        return r;
    }

    @Test
    void newRuleIsADraftThatDoesNotApply() {
        ContractRiskRule r = draft();
        assertEquals("DRAFT", r.getStatus());
        assertEquals("MEDIUM", r.getSeverity());
        assertEquals("SERVICE,LEASE", r.getAppliesToTypes());
        assertEquals("South Africa", r.getJurisdiction());
        assertFalse(r.active(today));
    }

    @Test
    void validatesInput() {
        UUID t = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, " ", null, "REQUIRES_VALUE", null, null, null, "LOW", false, "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "NOPE", null, null, null, "LOW", false, "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "REQUIRES_PHRASE", " ", null, null, "LOW", false, "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "VALUE_ABOVE", null, null, null, "LOW", false, "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "VALUE_ABOVE", null, new BigDecimal("-1"), null, "LOW", false, "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "REQUIRES_VALUE", null, null, null, "SEVERE", false, "m", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "REQUIRES_VALUE", null, null, null, "LOW", false, " ", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContractRiskRule.create(t, "n", null, "REQUIRES_CLAUSE_CATEGORY", "NOT_A_CATEGORY", null, null, "LOW", false, "m", null, null, null, null));
    }

    @Test
    void approvalNeedsTheReviewersDetailsAndASensibleDate() {
        assertThrows(IllegalArgumentException.class, () -> draft().approve(" ", "x", today, null, null, today));
        assertThrows(IllegalArgumentException.class, () -> draft().approve("A", " ", today, null, null, today));
        assertThrows(IllegalArgumentException.class, () -> draft().approve("A", "x", today.plusDays(1), null, null, today));
        assertThrows(IllegalArgumentException.class, () -> draft().approve("A", "x", today, today, null, today));
    }

    @Test
    void onlyAnApprovedRuleCanBePublished() {
        assertThrows(IllegalStateException.class, () -> draft().publish(today));
    }

    @Test
    void publishedRuleAppliesUntilItsReviewLapses() {
        ContractRiskRule r = published(today.plusDays(30));
        assertTrue(r.active(today));
        assertTrue(r.active(today.plusDays(30)));
        assertFalse(r.active(today.plusDays(31)));
        assertTrue(r.reviewExpired(today.plusDays(31)));
    }

    @Test
    void appliesToChecksTypeWhenRestrictedAndEverythingWhenNot() {
        ContractRiskRule r = draft();
        assertTrue(r.appliesTo("service"));
        assertFalse(r.appliesTo("EMPLOYMENT"));
        ContractRiskRule all = ContractRiskRule.create(UUID.randomUUID(), "n", null, "REQUIRES_VALUE", null, null, null, "LOW", false, "m", null, null, null, null);
        assertTrue(all.appliesTo("ANYTHING"));
    }

    @Test
    void publishedRuleCannotBeEditedButANewVersionCan() {
        ContractRiskRule r = published(null);
        assertThrows(IllegalStateException.class, () -> r.edit("n", null, "REQUIRES_VALUE", null, null, null, "LOW", false, "m", null, null, null));
        ContractRiskRule v2 = r.newVersion(2, UUID.randomUUID());
        assertEquals("DRAFT", v2.getStatus());
        assertEquals(2, v2.getVersionNo());
        assertEquals(r.getFamilyId(), v2.getFamilyId());
        assertNotEquals(r.getId(), v2.getId());
        assertNull(v2.getReviewerName(), "the new version must be reviewed again");
    }

    @Test
    void anApprovedRuleIsNoLongerEditable() {
        ContractRiskRule r = draft();
        r.approve("A", "x", today, null, null, today);
        assertThrows(IllegalStateException.class, () -> r.edit("n", null, "REQUIRES_VALUE", null, null, null, "LOW", false, "m", null, null, null));
    }

    @Test
    void supersedeAndWithdrawRules() {
        ContractRiskRule r = published(null);
        r.supersede();
        assertEquals("SUPERSEDED", r.getStatus());
        assertThrows(IllegalStateException.class, r::supersede);
        ContractRiskRule w = published(null);
        assertThrows(IllegalArgumentException.class, () -> w.withdraw(" "));
        w.withdraw("Law changed");
        assertEquals("WITHDRAWN", w.getStatus());
        assertFalse(w.active(today));
        assertThrows(IllegalStateException.class, () -> draft().withdraw("x"));
    }
}
