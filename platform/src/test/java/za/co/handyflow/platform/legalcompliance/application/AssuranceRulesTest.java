package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.AssuranceRules;
import za.co.handyflow.platform.legalcompliance.domain.model.AuditFindingType;
import za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.FindingSeverity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AssuranceRulesTest {

    @Test @DisplayName("an observation can be closed with no actions at all")
    void observationNeedsNoAction() {
        assertNull(AssuranceRules.closeBlocker(AuditFindingType.OBSERVATION, List.of()));
    }

    @Test @DisplayName("any other finding type needs at least one action")
    void deficiencyNeedsAnAction() {
        assertNotNull(AssuranceRules.closeBlocker(AuditFindingType.DEFICIENCY, List.of()));
        assertNotNull(AssuranceRules.closeBlocker(AuditFindingType.BREACH, List.of(CapaStatus.CANCELLED)), "a cancelled action does not count");
    }

    @Test @DisplayName("every live action must be verified; cancelled ones are ignored")
    void allLiveActionsVerified() {
        assertTrue(AssuranceRules.closeBlocker(AuditFindingType.NON_CONFORMANCE, List.of(CapaStatus.VERIFIED, CapaStatus.IN_PROGRESS)).contains("1 corrective"));
        assertNull(AssuranceRules.closeBlocker(AuditFindingType.NON_CONFORMANCE, List.of(CapaStatus.VERIFIED, CapaStatus.CANCELLED)));
    }

    @Test @DisplayName("blocking actions are those neither verified nor cancelled")
    void blockingCount() {
        assertEquals(3, AssuranceRules.blockingCapas(List.of(CapaStatus.PROPOSED, CapaStatus.COMPLETED, CapaStatus.APPROVED, CapaStatus.VERIFIED, CapaStatus.CANCELLED)));
        assertEquals(0, AssuranceRules.blockingCapas(List.of()));
    }

    @Test @DisplayName("auditor independence: owning a scoped control conflicts; unknown auditor or other owners do not")
    void independence() {
        UUID me = UUID.randomUUID();
        assertTrue(AssuranceRules.auditorOwnsScopeControl(me, List.of(UUID.randomUUID(), me)));
        assertFalse(AssuranceRules.auditorOwnsScopeControl(me, List.of(UUID.randomUUID())));
        assertFalse(AssuranceRules.auditorOwnsScopeControl(null, List.of(me)));
    }

    @Test @DisplayName("default due dates follow the remediation windows; overdue only counts open items")
    void dates() {
        LocalDate d = LocalDate.of(2026, 11, 19);
        assertEquals(d.plusDays(30), AssuranceRules.defaultDue(FindingSeverity.HIGH, d));
        assertEquals(d.plusDays(14), AssuranceRules.defaultDue(FindingSeverity.CRITICAL, d));
        assertTrue(AssuranceRules.isOverdue(d.minusDays(1), true, d));
        assertFalse(AssuranceRules.isOverdue(d.minusDays(1), false, d));
        assertFalse(AssuranceRules.isOverdue(d, true, d));
    }
}
