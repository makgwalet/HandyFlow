package za.co.handyflow.platform.contracting.api;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The temporary USER_READ / USER_UPDATE fallback was removed in V773; contracting endpoints must use CONTRACTS_* only. */
class ContractingNoLegacyAuthoritiesTest {

    @Test
    void controllersDoNotAcceptLegacyUserAuthorities() throws Exception {
        for (Class<?> c : List.of(ContractingController.class, ContractSharingController.class)) {
            int guarded = 0;
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) continue;
                guarded++;
                assertFalse(pa.value().contains("USER_"), c.getSimpleName() + "." + m.getName() + " still accepts a USER_* authority");
            }
            assertTrue(guarded > 0, c.getSimpleName() + " has no guarded methods");
        }
    }
}
