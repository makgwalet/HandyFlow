package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenPermissionsTest {

    private static final Map<String, String> MODULE_OF = Map.of(
            "USER_READ", "core",
            "HR_MANAGE", "hr",
            "LEAVE_APPROVE", "hr",
            "FLEET_READ", "fleet",
            "AP_PAY", "ap",
            "NEW_THING", "unassigned");
    private static final Set<String> CATALOGUED = Set.of("hr", "fleet");   // ap has no catalogue row

    private static Set<String> drop(Set<String> held, Map<String, Integer> perModule) {
        return TokenPermissions.toDrop(Set.of("USER_READ", "HR_MANAGE", "LEAVE_APPROVE", "FLEET_READ", "AP_PAY", "NEW_THING"),
                MODULE_OF, CATALOGUED, held::contains, perModule);
    }

    @Test void aTenantWithoutAModuleLosesThatModulesPermissions() {
        Map<String, Integer> per = new HashMap<>();
        assertEquals(Set.of("HR_MANAGE", "LEAVE_APPROVE"), drop(Set.of("fleet"), per));
        assertEquals(Map.of("hr", 2), per);
    }

    @Test void aTenantHoldingEveryModuleLosesNothing() {
        assertEquals(Set.of(), drop(Set.of("hr", "fleet"), new HashMap<>()));
    }

    @Test void corePermissionsAreNeverDropped() {
        assertEquals(Set.of("HR_MANAGE", "LEAVE_APPROVE", "FLEET_READ"), drop(Set.of(), new HashMap<>()));
    }

    @Test void modulesWithoutACatalogueRowAndUnassignedPermissionsAreNeverDropped() {
        // AP_PAY (module ap, no catalogue row) and NEW_THING (unassigned) stay even for a tenant holding nothing
        Set<String> dropped = drop(Set.of(), new HashMap<>());
        assertEquals(false, dropped.contains("AP_PAY"));
        assertEquals(false, dropped.contains("NEW_THING"));
    }

    @Test void anUnknownPermissionIsNeverDropped() {
        assertEquals(Set.of(), TokenPermissions.toDrop(Set.of("MYSTERY"), MODULE_OF, CATALOGUED, m -> false, new HashMap<>()));
    }

    @Test void anyFailureLeavesThePermissionsUnchanged() {
        // no database and no billing at all: forToken must return its input, not throw
        Set<String> all = Set.of("HR_MANAGE", "USER_READ");
        assertEquals(all, new TokenPermissions(null, null, true).forToken(UUID.randomUUID(), all));
    }
}
