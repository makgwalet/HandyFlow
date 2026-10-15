package za.co.handyflow.platform.earthmoving.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;

/**
 * Service-layer permission check for Earthmoving, so a rule holds even if a controller annotation is missed or a
 * second entry point is added later (patch E1). EARTHMOVING_ADMIN does not stand in for any other authority.
 */
@Component
public class EarthmovingAccess {

    public boolean has(String authority) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> authority.equals(g.getAuthority()));
    }

    public void require(String authority, String action) {
        if (!has(authority)) {
            throw new HandyFlowException("You need the " + authority + " permission to " + action + ".",
                    HttpStatus.FORBIDDEN, "EARTHMOVING_FORBIDDEN");
        }
    }
}
