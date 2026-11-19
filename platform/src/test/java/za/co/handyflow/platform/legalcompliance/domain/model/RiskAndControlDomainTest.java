package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RiskAndControlDomainTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID user = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 19);

    private Risk risk() {
        return Risk.create(tenant, "RSK-00001", "Unauthorised access to personal information", "desc", RiskCategory.COMPLIANCE, false, user, "Owner", today.plusMonths(3), user);
    }

    private RiskAssessment assessment(int il, int ii, int rl, int ri) {
        return RiskAssessment.create(tenant, UUID.randomUUID(), il, ii, rl, ri, 9, 16, "Reviewed with the information officer", user, "Assessor");
    }

    private Control control() {
        return Control.create(tenant, "CTL-00001", "Quarterly access review", "Only authorised staff access personal information", "Review the access register",
                ControlType.DETECTIVE, ControlNature.MANUAL, ControlFrequency.QUARTERLY, user, "Owner", "Signed access review report", user);
    }

    @Test
    @DisplayName("a new assessment stores scores, ratings and the thresholds in force")
    void assessmentStoresThresholds() {
        RiskAssessment a = assessment(4, 5, 2, 3);
        assertEquals(20, a.getInherentScore());
        assertEquals(RiskRating.CRITICAL, a.getInherentRating());
        assertEquals(6, a.getResidualScore());
        assertEquals(RiskRating.MEDIUM, a.getResidualRating());
        assertEquals(9, a.getAppetiteMax());
        assertEquals(RiskPosition.WITHIN_APPETITE, a.residualPosition());
        assertTrue(a.isInForce());
    }

    @Test
    @DisplayName("residual risk can never exceed inherent, and a rationale is required")
    void assessmentRules() {
        assertThrows(IllegalArgumentException.class, () -> assessment(2, 2, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> RiskAssessment.create(tenant, UUID.randomUUID(), 3, 3, 2, 2, 9, 16, "short", user, "A"));
    }

    @Test
    @DisplayName("superseding marks the assessment as no longer in force and keeps its scores")
    void supersede() {
        RiskAssessment a = assessment(3, 3, 2, 2);
        a.supersede();
        assertFalse(a.isInForce());
        assertNotNull(a.getSupersededAt());
        assertEquals(9, a.getInherentScore());
    }

    @Test
    @DisplayName("risk lifecycle: assess, treat, accept, lapse, close, reopen")
    void riskLifecycle() {
        Risk r = risk();
        assertEquals(RiskStatus.IDENTIFIED, r.getStatus());
        assertThrows(IllegalStateException.class, () -> r.startTreatment(TreatmentStrategy.MITIGATE, "plan", today.plusDays(30)));
        r.markAssessed();
        assertEquals(RiskStatus.ASSESSED, r.getStatus());
        r.startTreatment(TreatmentStrategy.MITIGATE, "Roll out quarterly access reviews", today.plusDays(30));
        assertEquals(RiskStatus.TREATING, r.getStatus());
        r.accept(user, "Exec", "Residual risk is within what the board will carry", today.plusMonths(6), today);
        assertEquals(RiskStatus.ACCEPTED, r.getStatus());
        assertEquals("Exec", r.getAcceptedByName());
        r.lapseAcceptance();
        assertEquals(RiskStatus.ASSESSED, r.getStatus());
        assertNull(r.getAcceptedBy());
        r.close("Process discontinued");
        assertFalse(r.isOpen());
        assertThrows(IllegalStateException.class, () -> r.update("t", null, RiskCategory.LEGAL, false, user, "O", today));
        r.reopen();
        assertEquals(RiskStatus.ASSESSED, r.getStatus());
    }

    @Test
    @DisplayName("an acceptance needs a reason and a future expiry")
    void acceptanceRules() {
        Risk r = risk();
        r.markAssessed();
        assertThrows(IllegalArgumentException.class, () -> r.accept(user, "E", "too short", today.plusDays(10), today));
        assertThrows(IllegalArgumentException.class, () -> r.accept(user, "E", "A sufficiently long reason", today, today));
        assertThrows(IllegalArgumentException.class, () -> r.accept(user, "E", "A sufficiently long reason", null, today));
    }

    @Test
    @DisplayName("planning treatment withdraws an existing acceptance")
    void treatmentClearsAcceptance() {
        Risk r = risk();
        r.markAssessed();
        r.accept(user, "E", "A sufficiently long reason", today.plusDays(90), today);
        r.startTreatment(TreatmentStrategy.TRANSFER, "Buy cover", today.plusDays(20));
        assertEquals(RiskStatus.TREATING, r.getStatus());
        assertNull(r.getAcceptanceExpires());
    }

    @Test
    @DisplayName("a new control is a DRAFT, NOT_ASSESSED and NOT_TESTED")
    void controlStartsUntested() {
        Control c = control();
        assertEquals(ControlStatus.DRAFT, c.getStatus());
        assertEquals(DesignRating.NOT_ASSESSED, c.getDesignRating());
        assertEquals(OperatingEffectiveness.NOT_TESTED, c.getOperatingEffectiveness());
    }

    @Test
    @DisplayName("activating a control never makes it effective")
    void activationIsNotEffectiveness() {
        Control c = control();
        c.assessDesign(DesignRating.ADEQUATE, null, user, "Assessor");
        c.activate();
        assertTrue(c.isActive());
        assertEquals(OperatingEffectiveness.NOT_TESTED, c.getOperatingEffectiveness());
        assertNull(c.getLastTestedAt());
    }

    @Test
    @DisplayName("activation needs objective, procedure and expected evidence")
    void activationPrerequisites() {
        Control c = Control.create(tenant, "CTL-2", "Bare control", null, null, ControlType.PREVENTIVE, ControlNature.AUTOMATED, ControlFrequency.CONTINUOUS, user, "O", null, user);
        assertThrows(IllegalArgumentException.class, c::activate);
        assertEquals(ControlStatus.DRAFT, c.getStatus());
    }

    @Test
    @DisplayName("a poor design rating needs an explanation; an adequate one does not")
    void designAssessment() {
        Control c = control();
        assertThrows(IllegalArgumentException.class, () -> c.assessDesign(DesignRating.INADEQUATE, "bad", user, "A"));
        assertThrows(IllegalArgumentException.class, () -> c.assessDesign(DesignRating.NOT_ASSESSED, null, user, "A"));
        c.assessDesign(DesignRating.ADEQUATE, null, user, "Assessor");
        assertEquals(DesignRating.ADEQUATE, c.getDesignRating());
    }

    @Test
    @DisplayName("a material change resets the design assessment; a change of owner does not")
    void materialChange() {
        Control c = control();
        c.assessDesign(DesignRating.ADEQUATE, null, user, "Assessor");
        assertFalse(c.update("Quarterly access review", c.getObjective(), c.getProcedureText(), c.getControlType(), c.getNature(), c.getFrequency(), user, "New owner", c.getExpectedEvidence()));
        assertEquals(DesignRating.ADEQUATE, c.getDesignRating());
        assertTrue(c.update("Quarterly access review", c.getObjective(), c.getProcedureText(), c.getControlType(), c.getNature(), ControlFrequency.MONTHLY, user, "New owner", c.getExpectedEvidence()));
        assertEquals(DesignRating.NOT_ASSESSED, c.getDesignRating());
        assertNull(c.getDesignAssessedByName());
    }

    @Test
    @DisplayName("a retired control cannot be edited, assessed or retired again, and needs a reason")
    void retire() {
        Control c = control();
        assertThrows(IllegalArgumentException.class, () -> c.retire("short"));
        c.retire("Replaced by an automated control");
        assertEquals(ControlStatus.RETIRED, c.getStatus());
        assertThrows(IllegalStateException.class, () -> c.retire("Replaced by an automated control"));
        assertThrows(IllegalStateException.class, () -> c.assessDesign(DesignRating.ADEQUATE, null, user, "A"));
        assertThrows(IllegalStateException.class, () -> c.update("t", null, null, ControlType.PREVENTIVE, ControlNature.MANUAL, ControlFrequency.DAILY, user, "O", null));
    }
}
