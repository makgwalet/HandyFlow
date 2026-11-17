package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftVariancePolicy;
import za.co.handyflow.platform.fuel.domain.model.FuelToleranceOverride;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftVariancePolicyRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelToleranceOverrideRepository;
import za.co.handyflow.platform.fuel.dto.ShiftControlDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tolerances a company sets beyond its one company-wide number: a cash-variance tolerance per depot (site), a gauge difference per fuel type,
 * and the rule for repeated variances. Every number is the company's own; none is built in. With nothing set, the company-wide tolerance applies.
 * A shift that draws on tanks in more than one depot with their own tolerances uses the smallest of them, so it is never looser than any site's rule.
 */
@Service
@RequiredArgsConstructor
public class FuelToleranceService {

    private final FuelToleranceOverrideRepository overrides;
    private final FuelShiftVariancePolicyRepository policies;
    private final FuelDepotRepository depots;

    @Transactional(readOnly = true)
    public Controls get(TenantId tenant) {
        Map<UUID, String> names = depots.findAllActive(tenant).stream().collect(Collectors.toMap(FuelDepot::getId, FuelDepot::getName, (a, b) -> a));
        List<OverrideView> out = new ArrayList<>();
        for (FuelToleranceOverride o : overrides.findAllForTenant(tenant)) {
            String label = FuelToleranceOverride.DEPOT.equals(o.getScope()) ? names.getOrDefault(parse(o.getScopeKey()), "Depot no longer active") : o.getScopeKey();
            out.add(new OverrideView(o.getScope(), o.getScopeKey(), label, o.getAmount()));
        }
        Policy p = policies.findById(tenant.getValue()).map(x -> new Policy(x.getRepeatCount(), x.getRepeatDays())).orElse(new Policy(null, null));
        return new Controls(out, p);
    }

    @Transactional
    public OverrideView save(TenantId tenant, OverrideRequest req, UUID by) {
        String scope = req.scope() == null ? "" : req.scope().trim().toUpperCase(Locale.ROOT);
        String key = req.key().trim();
        String label;
        if (FuelToleranceOverride.DEPOT.equals(scope)) {
            UUID depotId = parse(key);
            FuelDepot d = depotId == null ? null : depots.findActiveById(tenant, depotId).orElse(null);
            if (d == null) throw new ResourceNotFoundException("Depot", key);
            key = d.getId().toString(); label = d.getName();
        } else if (FuelToleranceOverride.PRODUCT.equals(scope)) {
            key = key.toUpperCase(Locale.ROOT); label = key;
        } else {
            throw new IllegalArgumentException("Choose a depot or a fuel type.");
        }
        final String k = key;
        FuelToleranceOverride o = overrides.findOne(tenant, scope, k).orElse(null);
        if (o == null) o = overrides.save(FuelToleranceOverride.of(tenant, scope, k, req.amount(), by)); else o.set(req.amount(), by);
        return new OverrideView(scope, k, label, o.getAmount());
    }

    @Transactional
    public void remove(TenantId tenant, String scope, String key) {
        String s = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
        String k = FuelToleranceOverride.PRODUCT.equals(s) && key != null ? key.trim().toUpperCase(Locale.ROOT) : key;
        overrides.findOne(tenant, s, k).ifPresent(overrides::delete);
    }

    /** Null count switches the repeated-variance alert off. */
    @Transactional
    public Policy savePolicy(TenantId tenant, Integer count, Integer days, UUID by) {
        if (count == null) {
            policies.findById(tenant.getValue()).ifPresent(policies::delete);
            return new Policy(null, null);
        }
        if (days == null) throw new IllegalArgumentException("Say how many days to count the shifts over.");
        FuelShiftVariancePolicy p = policies.findById(tenant.getValue()).orElse(null);
        if (p == null) p = policies.save(FuelShiftVariancePolicy.of(tenant, count, days, by)); else p.set(count, days, by);
        return new Policy(p.getRepeatCount(), p.getRepeatDays());
    }

    @Transactional(readOnly = true)
    public Optional<FuelShiftVariancePolicy> policy(TenantId tenant) { return policies.findById(tenant.getValue()); }

    /** The cash-variance tolerance for a shift that drew on these depots: the smallest depot override that applies, else the company-wide number. Never throws. */
    public BigDecimal cashToleranceFor(TenantId tenant, Collection<UUID> depotIds, BigDecimal companyWide) {
        try {
            BigDecimal best = null;
            for (UUID d : new HashSet<>(depotIds == null ? List.of() : depotIds)) {
                if (d == null) continue;
                BigDecimal v = overrides.findOne(tenant, FuelToleranceOverride.DEPOT, d.toString()).map(FuelToleranceOverride::getAmount).orElse(null);
                if (v != null && (best == null || v.compareTo(best) < 0)) best = v;
            }
            return best != null ? best : companyWide;
        } catch (RuntimeException ex) {
            return companyWide;
        }
    }

    /** The gauge difference limit for a fuel type, else the company-wide limit. Never throws. */
    public BigDecimal gaugeLimitFor(TenantId tenant, String fuelType, BigDecimal companyWide) {
        try {
            if (fuelType == null) return companyWide;
            return overrides.findOne(tenant, FuelToleranceOverride.PRODUCT, fuelType.trim().toUpperCase(Locale.ROOT)).map(FuelToleranceOverride::getAmount).orElse(companyWide);
        } catch (RuntimeException ex) {
            return companyWide;
        }
    }

    private static UUID parse(String s) {
        try { return UUID.fromString(s); } catch (RuntimeException e) { return null; }
    }
}
