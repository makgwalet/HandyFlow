package za.co.handyflow.platform.compliancetender.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The registration rule on a tracked requirement (business readiness): trimmed, blank means none, and versions carry or replace it. */
class ComplianceRequirementRuleTest {

    static final TenantId TENANT = TenantId.generate();
    static final UUID USER = UUID.randomUUID();
    static final UUID CLIENT = UUID.randomUUID();

    private static ComplianceRequirement create(String authority, String type) {
        return ComplianceRequirement.create(TENANT, "csd_active", "Valid CSD", null, "CSD Report", true, authority, type, USER);
    }
    private static ComplianceRequirement legacy() {
        return ComplianceRequirement.create(TENANT, "CSD_ACTIVE", "Valid CSD", null, null, true, USER);
    }

    @Test
    @DisplayName("a rule is stored trimmed")
    void trimmed() {
        ComplianceRequirement r = create("  CSD ", " Supplier  ");
        assertEquals("CSD", r.getSatisfiedByAuthority()); assertEquals("Supplier", r.getSatisfiedByRegistrationType());
    }

    @Test
    @DisplayName("blank or missing parts mean no rule for that part")
    void blankIsNone() {
        ComplianceRequirement r = create("   ", "");
        assertNull(r.getSatisfiedByAuthority()); assertNull(r.getSatisfiedByRegistrationType());
        assertNull(create(null, null).getSatisfiedByAuthority());
    }

    @Test
    @DisplayName("the document that satisfies a requirement is the existing evidenceType, untouched by the rule")
    void evidenceTypeUnchanged() {
        assertEquals("CSD Report", create("CSD", "Supplier").getEvidenceType());
    }

    @Test
    @DisplayName("a requirement created the old way has no rule")
    void legacyCreateHasNoRule() {
        ComplianceRequirement r = legacy();
        assertNull(r.getSatisfiedByAuthority()); assertNull(r.getSatisfiedByRegistrationType()); assertEquals(1, r.getRequirementVersion());
    }

    @Test
    @DisplayName("the old newVersion() keeps the existing rule")
    void legacyNewVersionKeepsTheRule() {
        ComplianceRequirement v2 = create("CSD", "Supplier").newVersion("Renamed", null, "CSD Report", true, USER);
        assertEquals("CSD", v2.getSatisfiedByAuthority()); assertEquals("Supplier", v2.getSatisfiedByRegistrationType()); assertEquals(2, v2.getRequirementVersion());
    }

    @Test
    @DisplayName("newVersion() with an explicit rule replaces it, with null clears it, and the original is never changed")
    void explicitNewVersion() {
        ComplianceRequirement v1 = create("CSD", "Supplier");
        ComplianceRequirement replaced = v1.newVersion("N", null, null, true, "CIDB", "Contractor", USER);
        ComplianceRequirement cleared = v1.newVersion("N", null, null, true, null, null, USER);
        assertEquals("CIDB", replaced.getSatisfiedByAuthority()); assertEquals("Contractor", replaced.getSatisfiedByRegistrationType());
        assertNull(cleared.getSatisfiedByAuthority()); assertNull(cleared.getSatisfiedByRegistrationType());
        assertEquals("CSD", v1.getSatisfiedByAuthority()); assertEquals("Supplier", v1.getSatisfiedByRegistrationType());
    }

    @Test
    @DisplayName("a new version keeps the code (and the client, where there is one) and goes up one version")
    void versionAndIdentity() {
        ComplianceRequirement v2 = create("CSD", "Supplier").newVersion("N", null, null, true, "CSD", "Supplier", USER);
        assertEquals("CSD_ACTIVE", v2.getCode()); assertEquals(2, v2.getRequirementVersion());
    }
}
