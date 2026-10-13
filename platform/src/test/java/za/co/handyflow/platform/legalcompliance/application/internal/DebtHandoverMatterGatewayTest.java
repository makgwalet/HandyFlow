package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.LegalMatterGateway.HandoverTransfer;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatterType;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebtHandoverMatterGatewayTest {

    @Test
    void mapsAgreementTypeToMatterType() {
        assertEquals(LitigationMatterType.CIVIL, DebtHandoverMatterGateway.mapType("NCA_CREDIT_AGREEMENT"));
        assertEquals(LitigationMatterType.COMMERCIAL, DebtHandoverMatterGateway.mapType("OTHER_COMMERCIAL"));
        assertEquals(LitigationMatterType.OTHER, DebtHandoverMatterGateway.mapType("UNKNOWN"));
        assertEquals(LitigationMatterType.OTHER, DebtHandoverMatterGateway.mapType(null));
    }

    @Test
    void descriptionCarriesProvenanceSoItSurvivesAFailedAttachment() {
        UUID handover = UUID.randomUUID();
        HandoverTransfer t = new HandoverTransfer(null, handover, UUID.randomUUID(), "DC-00042", "ABC Trading",
                null, new BigDecimal("84330.5"), "OTHER_COMMERCIAL", "{}", "deadbeef", null, Set.of(), null);
        String d = DebtHandoverMatterGateway.describe(t);
        assertTrue(d.contains("DC-00042"));
        assertTrue(d.contains(handover.toString()));
        assertTrue(d.contains("deadbeef"));
        assertTrue(d.contains("R84330.50"));
    }
}
