package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractApprovalTest {

    UUID tenant = UUID.randomUUID(), maker = UUID.randomUUID(), checker = UUID.randomUUID();

    ContractApproval req() {
        return ContractApproval.request(tenant, ApprovalAction.SEND_FOR_SIGNING, UUID.randomUUID(), "CTR-1", "h", maker, "please");
    }

    @Test
    void anotherPersonCanApprove() {
        ContractApproval a = req();
        a.approve(checker, "ok", false, null, true);
        assertTrue(a.isApproved());
        assertEquals(checker, a.getDecidedBy());
        assertFalse(a.isSoleUserException());
    }

    @Test
    void theRequesterCannotApproveTheirOwnRequestWithoutAnException() {
        ContractApproval a = req();
        assertThrows(IllegalStateException.class, () -> a.approve(maker, null, false, null, true));
        assertTrue(a.isPending());
    }

    @Test
    void aSoleUserExceptionNeedsTheRequesterAReasonAndPolicyPermission() {
        ContractApproval a = req();
        assertThrows(IllegalArgumentException.class, () -> a.approve(maker, null, true, "short", true));
        assertThrows(IllegalArgumentException.class, () -> a.approve(maker, null, true, null, true));
        assertThrows(IllegalStateException.class, () -> a.approve(maker, null, true, "I am the only person in the company", false));
        assertThrows(IllegalStateException.class, () -> a.approve(checker, null, true, "I am the only person in the company", true));
        assertTrue(a.isPending());
        a.approve(maker, null, true, "  I am the only person in the company  ", true);
        assertTrue(a.isApproved());
        assertTrue(a.isSoleUserException());
        assertEquals("I am the only person in the company", a.getExceptionReason());
    }

    @Test
    void rejectingNeedsAnotherPersonAndAReason() {
        ContractApproval a = req();
        assertThrows(IllegalStateException.class, () -> a.reject(maker, "no"));
        assertThrows(IllegalArgumentException.class, () -> a.reject(checker, " "));
        a.reject(checker, "Clause 4 is wrong");
        assertEquals("REJECTED", a.getStatus());
        assertNotNull(a.getClosedAt());
        assertThrows(IllegalStateException.class, () -> a.approve(checker, null, false, null, true));
    }

    @Test
    void onlyTheRequesterCanWithdrawAndOnlyWhileOpen() {
        ContractApproval a = req();
        assertThrows(IllegalStateException.class, () -> a.cancel(checker));
        a.cancel(maker);
        assertEquals("CANCELLED", a.getStatus());
        assertThrows(IllegalStateException.class, () -> a.cancel(maker));
    }

    @Test
    void anApprovalCanBeUsedOnceAndInvalidationOnlyAffectsOpenOnes() {
        ContractApproval a = req();
        assertThrows(IllegalStateException.class, a::consume);   // not approved yet
        a.approve(checker, null, false, null, true);
        a.consume();
        assertEquals("CONSUMED", a.getStatus());
        assertThrows(IllegalStateException.class, a::consume);
        a.invalidate();
        assertEquals("CONSUMED", a.getStatus());

        ContractApproval b = req();
        b.approve(checker, null, false, null, true);
        b.invalidate();
        assertEquals("INVALIDATED", b.getStatus());
    }

    @Test
    void approverRolesDependOnTheAction() {
        assertTrue(ApprovalAction.SEND_FOR_SIGNING.canBeDecidedBy(Set.of("CONTRACTS_MANAGE")));
        assertFalse(ApprovalAction.SEND_FOR_SIGNING.canBeDecidedBy(Set.of("CONTRACTS_TEMPLATES", "CONTRACTS_READ")));
        assertTrue(ApprovalAction.PUBLISH_CLAUSE.canBeDecidedBy(Set.of("CONTRACTS_TEMPLATES")));
        assertFalse(ApprovalAction.PUBLISH_CLAUSE.canBeDecidedBy(Set.of("CONTRACTS_MANAGE", "CONTRACTS_SIGN")));
        assertTrue(ApprovalAction.PUBLISH_TEMPLATE.canBeDecidedBy(Set.of("CONTRACTS_ADMIN")));
        assertFalse(ApprovalAction.TERMINATE.canBeDecidedBy(null));
    }

    @Test
    void safeDefaults() {
        assertTrue(ApprovalAction.SEND_FOR_SIGNING.requiredByDefault());
        assertTrue(ApprovalAction.PUBLISH_CLAUSE.requiredByDefault());
        assertTrue(ApprovalAction.PUBLISH_TEMPLATE.requiredByDefault());
        assertFalse(ApprovalAction.TERMINATE.requiredByDefault());
    }
}
