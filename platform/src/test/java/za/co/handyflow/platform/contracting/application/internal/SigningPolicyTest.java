package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.SigningMethod;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SigningPolicyTest {

    private final SigningPolicy policy = new SigningPolicy(List.of(new SimpleElectronicSigningProvider()));

    private Contract contract(String required) {
        Contract c = Contract.create(TenantId.of(UUID.randomUUID()), "CT-1", "t", "NDA", "<p>x</p>", null, UUID.randomUUID());
        if (required != null) c.setRequiredSigningMethod(required);
        return c;
    }

    @Test
    void unclassifiedAndSimpleContractsMaySign() {
        assertDoesNotThrow(() -> policy.assertCanSign(contract(null)));
        assertDoesNotThrow(() -> policy.assertCanSign(contract("SIMPLE_ELECTRONIC")));
    }

    @Test
    void contractsNeedingAStrongerMethodAreBlockedNotDowngraded() {
        for (String m : new String[]{"ADVANCED_ELECTRONIC", "WET_INK", "IN_PERSON"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> policy.assertCanSign(contract(m)));
            assertTrue(e.getMessage().contains(m));
        }
    }

    @Test
    void onlySimpleElectronicIsSupportedToday() {
        assertTrue(policy.supports(SigningMethod.SIMPLE_ELECTRONIC));
        assertFalse(policy.supports(SigningMethod.ADVANCED_ELECTRONIC));
    }
}
