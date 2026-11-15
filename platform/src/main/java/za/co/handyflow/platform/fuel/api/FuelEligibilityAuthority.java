package za.co.handyflow.platform.fuel.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Whether the signed-in user holds the permission to allocate a load that has eligibility review points. */
final class FuelEligibilityAuthority {
    private FuelEligibilityAuthority() {}

    static boolean canReview() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "FUEL_ELIGIBILITY_REVIEW".equals(g.getAuthority()));
    }
}
