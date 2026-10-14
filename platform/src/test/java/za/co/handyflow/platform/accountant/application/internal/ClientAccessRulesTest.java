package za.co.handyflow.platform.accountant.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Finance patch F15: which clients staff can see. */
class ClientAccessRulesTest {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test void staffWithAllClientsSeeEverything() {
        assertTrue(ClientAccessRules.canAccess(true, Set.of(), a));
        assertEquals(3, ClientAccessRules.visible(true, Set.of(), List.of(a, b, c), x -> x).size());
    }

    @Test void othersSeeOnlyAssignedClients() {
        Set<UUID> mine = Set.of(a, c);
        assertTrue(ClientAccessRules.canAccess(false, mine, a));
        assertFalse(ClientAccessRules.canAccess(false, mine, b));
        assertFalse(ClientAccessRules.canAccess(false, mine, null));
        assertFalse(ClientAccessRules.canAccess(false, null, a));
        assertEquals(List.of(a, c), ClientAccessRules.visible(false, mine, List.of(a, b, c), x -> x));
    }

    @Test void assignments() {
        assertNull(ClientAccessRules.assignProblem("PREPARER", false));
        assertNotNull(ClientAccessRules.assignProblem("BOSS", false));
        assertNotNull(ClientAccessRules.assignProblem(null, false));
        assertNotNull(ClientAccessRules.assignProblem("PARTNER", true));
    }
}
