package za.co.handyflow.platform.fuel.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class FuelComplianceAuthorityTest {

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void as(String... authorities) {
        var list = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", "p", list));
    }

    @Test @DisplayName("someone with only Fuel read access sees the status only")
    void readOnly() { as("FUEL_READ"); assertFalse(FuelComplianceAuthority.canSeeDetail()); }

    @Test @DisplayName("compliance managers, verifiers and general managers see the details")
    void detail() {
        for (String a : new String[]{"FUEL_COMPLIANCE_MANAGE", "FUEL_COMPLIANCE_VERIFY", "FUEL_MANAGE"}) { as("FUEL_READ", a); assertTrue(FuelComplianceAuthority.canSeeDetail(), a); }
    }

    @Test @DisplayName("nobody signed in sees the status only")
    void anonymous() { SecurityContextHolder.clearContext(); assertFalse(FuelComplianceAuthority.canSeeDetail()); }
}
