package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelDivisionActivation;
import za.co.handyflow.platform.fuel.domain.model.FuelOperatingProfile;
import za.co.handyflow.platform.fuel.domain.repository.FuelDivisionActivationRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelOperatingProfileRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides which divisions a tenant may use. This is one of the four controls (entitlement, division, capability,
 * permission): a user permission never overrides it. A tenant with no profile has no division active.
 * A legacy-mode tenant has every division, so nothing it used before divisions existed is switched off.
 */
@Service
@RequiredArgsConstructor
public class FuelDivisionGuard {

    private final FuelOperatingProfileRepository profiles;
    private final FuelDivisionActivationRepository activations;

    @Transactional(readOnly = true)
    public Set<FuelDivision> activeDivisions(TenantId tenantId) {
        Optional<FuelOperatingProfile> p = profiles.findByTenant(tenantId);
        if (p.isEmpty()) return EnumSet.noneOf(FuelDivision.class);
        if (p.get().isLegacyMode()) return EnumSet.allOf(FuelDivision.class);
        Set<FuelDivision> out = EnumSet.noneOf(FuelDivision.class);
        for (FuelDivisionActivation a : activations.findByTenant(tenantId)) if (a.isActive()) out.add(a.getDivision());
        return out;
    }

    /** Throws 403 unless at least one of the divisions is active. An empty requirement means shared foundation and always passes. */
    @Transactional(readOnly = true)
    public void require(TenantId tenantId, Set<FuelDivision> anyOf) {
        if (anyOf == null || anyOf.isEmpty()) return;
        Set<FuelDivision> active = activeDivisions(tenantId);
        for (FuelDivision d : anyOf) if (active.contains(d)) return;
        String names = anyOf.stream().sorted().map(FuelDivisionRules::label).collect(Collectors.joining(" or "));
        throw new HandyFlowException("This needs the " + names + " division, which is not active for your company. "
                + "An administrator can activate it under Fuel > Setup.", HttpStatus.FORBIDDEN, "FUEL_DIVISION_NOT_ACTIVE");
    }
}
