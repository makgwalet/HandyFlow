package za.co.handyflow.platform.fuel.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * Whether the signed-in user may see compliance details and documents, or only the status. Holders of the compliance, verify or general
 * manage permission see everything; anyone else with Fuel read access sees what each item is, who or where it is for, when it expires and
 * its status. Decided here on the server, never by the screen.
 */
final class FuelComplianceAuthority {
    static final Set<String> DETAIL = Set.of("FUEL_MANAGE", "FUEL_COMPLIANCE_MANAGE", "FUEL_COMPLIANCE_VERIFY");
    private FuelComplianceAuthority() {}

    static boolean canSeeDetail() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> DETAIL.contains(g.getAuthority()));
    }
}
