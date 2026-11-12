package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.model.FuelUserScope;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelUserScopeRepository;
import za.co.handyflow.platform.fuel.dto.ScopeDtos.MyScope;
import za.co.handyflow.platform.fuel.dto.ScopeDtos.SaveScopeRequest;
import za.co.handyflow.platform.fuel.dto.ScopeDtos.UserScope;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Phase 8c (G01): limits a user to some divisions and some depots. This is one more control beside the tenant's division
 * rules, never a replacement: a user's permission still has to allow the call, and the company's divisions still have to be active.
 * <ul>
 *   <li>A user with no scope rows is unrestricted. Existing users therefore keep exactly the access they have.</li>
 *   <li>Division rows: a division-gated route is allowed only if the user has at least one of the route's divisions.</li>
 *   <li>Depot rows: a route about one tank or one depot is allowed only if that tank sits in one of the user's depots. A tank with no depot is
 *       refused to a depot-limited user. Tank and depot lists are filtered the same way.</li>
 *   <li>Shared lists and reports that are not tied to one tank or depot are not depot-filtered here (see the gap register).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FuelScopeService {

    private final FuelUserScopeRepository scopes;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;

    /** What a user may reach. Empty sets mean no limit of that kind. */
    public record Scope(Set<FuelDivision> divisions, Set<UUID> depotIds) {
        public static final Scope UNRESTRICTED = new Scope(Set.of(), Set.of());

        public boolean restricted() { return !divisions.isEmpty() || !depotIds.isEmpty(); }

        public boolean allowsAnyOf(Set<FuelDivision> required) {
            if (divisions.isEmpty() || required == null || required.isEmpty()) return true;
            for (FuelDivision d : required) if (divisions.contains(d)) return true;
            return false;
        }

        public boolean allowsDepot(UUID depotId) {
            if (depotIds.isEmpty()) return true;
            return depotId != null && depotIds.contains(depotId);
        }
    }

    /** What an API path is about, when it names one tank or one depot. */
    public record Target(Kind kind, UUID id) { public enum Kind { TANK, DEPOT } }

    /** Finds a tank or depot id in a Fuel path such as /api/v1/fuel/tanks/{id}/receive or /api/v1/fuel/forecourt/tanks/{id}/reconciliation. */
    public static Optional<Target> targetOf(String path) {
        if (path == null || !path.startsWith(FuelDivisionRules.PREFIX)) return Optional.empty();
        String[] seg = path.substring(FuelDivisionRules.PREFIX.length()).split("/");
        // seg[0] is empty because the remainder starts with "/"
        List<String> parts = new ArrayList<>();
        for (String s : seg) if (!s.isEmpty()) parts.add(s);
        int at = !parts.isEmpty() && "forecourt".equals(parts.get(0)) ? 1 : 0;
        if (parts.size() <= at + 1) return Optional.empty();
        String kind = parts.get(at);
        Target.Kind k = "tanks".equals(kind) ? Target.Kind.TANK : "depots".equals(kind) ? Target.Kind.DEPOT : null;
        if (k == null) return Optional.empty();
        try { return Optional.of(new Target(k, UUID.fromString(parts.get(at + 1)))); }
        catch (IllegalArgumentException e) { return Optional.empty(); } // e.g. /tanks/utilization-forecast
    }

    @Transactional(readOnly = true)
    public Scope scopeOf(TenantId tenantId, UUID userId) {
        if (userId == null) return Scope.UNRESTRICTED;
        Set<FuelDivision> divisions = EnumSet.noneOf(FuelDivision.class);
        Set<UUID> depotIds = new HashSet<>();
        for (FuelUserScope s : scopes.findByUser(tenantId, userId)) {
            if (s.getDivision() != null) divisions.add(s.getDivision());
            if (s.getDepotId() != null) depotIds.add(s.getDepotId());
        }
        if (divisions.isEmpty() && depotIds.isEmpty()) return Scope.UNRESTRICTED;
        return new Scope(divisions, depotIds);
    }

    /** Throws 403 when the user's limits exclude every division the route belongs to. */
    @Transactional(readOnly = true)
    public void requireDivision(TenantId tenantId, UUID userId, Set<FuelDivision> anyOf) {
        if (anyOf == null || anyOf.isEmpty()) return;
        Scope s = scopeOf(tenantId, userId);
        if (s.allowsAnyOf(anyOf)) return;
        String names = anyOf.stream().sorted().map(FuelDivisionRules::label).collect(java.util.stream.Collectors.joining(" or "));
        throw new HandyFlowException("Your access is limited to other divisions, so you cannot use " + names + ". An administrator can change this under Fuel > Setup > Access limits.",
                HttpStatus.FORBIDDEN, "FUEL_SCOPE_DIVISION");
    }

    /** Throws 403 when the path names a tank or depot outside the user's depots. A tank or depot that does not exist is left for the controller to report. */
    @Transactional(readOnly = true)
    public void requireTarget(TenantId tenantId, UUID userId, Target target) {
        if (target == null) return;
        Scope s = scopeOf(tenantId, userId);
        if (s.depotIds().isEmpty()) return;
        UUID depotId;
        if (target.kind() == Target.Kind.DEPOT) depotId = target.id();
        else {
            Optional<FuelTank> t = tanks.findActiveById(tenantId, target.id());
            if (t.isEmpty()) return;
            depotId = t.get().getDepotId();
        }
        if (!s.allowsDepot(depotId)) {
            throw new HandyFlowException("Your access is limited to other depots, so you cannot use this one. An administrator can change this under Fuel > Setup > Access limits.",
                    HttpStatus.FORBIDDEN, "FUEL_SCOPE_DEPOT");
        }
    }

    /** The tank ids the user may see, or null when the user has no depot limit. */
    @Transactional(readOnly = true)
    public Set<UUID> allowedTankIds(TenantId tenantId, UUID userId) {
        Scope s = scopeOf(tenantId, userId);
        if (s.depotIds().isEmpty()) return null;
        Set<UUID> out = new HashSet<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) if (t.getDepotId() != null && s.depotIds().contains(t.getDepotId())) out.add(t.getId());
        return out;
    }

    /** The depot ids the user may see, or null when the user has no depot limit. */
    @Transactional(readOnly = true)
    public Set<UUID> allowedDepotIds(TenantId tenantId, UUID userId) {
        Scope s = scopeOf(tenantId, userId);
        return s.depotIds().isEmpty() ? null : s.depotIds();
    }

    // ── administration ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserScope> list(TenantId tenantId) {
        Map<UUID, List<String>> divisions = new LinkedHashMap<>();
        Map<UUID, List<UUID>> depotIds = new LinkedHashMap<>();
        for (FuelUserScope s : scopes.findAllForTenant(tenantId)) {
            divisions.computeIfAbsent(s.getUserId(), k -> new ArrayList<>());
            depotIds.computeIfAbsent(s.getUserId(), k -> new ArrayList<>());
            if (s.getDivision() != null) divisions.get(s.getUserId()).add(s.getDivision().name());
            if (s.getDepotId() != null) depotIds.get(s.getUserId()).add(s.getDepotId());
        }
        List<UserScope> out = new ArrayList<>();
        divisions.forEach((u, d) -> out.add(new UserScope(u, d, depotIds.get(u))));
        return out;
    }

    /** Replaces the user's whole set of limits. Empty lists remove every limit, which makes the user unrestricted again. */
    @Transactional
    public UserScope replace(TenantId tenantId, UUID userId, SaveScopeRequest req, UUID by) {
        List<String> wanted = req == null || req.divisions() == null ? List.of() : req.divisions();
        List<UUID> wantedDepots = req == null || req.depotIds() == null ? List.of() : req.depotIds();
        Set<FuelDivision> divisions = EnumSet.noneOf(FuelDivision.class);
        for (String d : wanted) {
            try { divisions.add(FuelDivision.valueOf(d)); }
            catch (IllegalArgumentException | NullPointerException e) { throw new IllegalArgumentException("Unknown division: " + d); }
        }
        Set<UUID> depotSet = new LinkedHashSet<>(wantedDepots);
        for (UUID depotId : depotSet) {
            if (depotId == null || depots.findActiveById(tenantId, depotId).isEmpty()) throw new IllegalArgumentException("A depot in the list was not found.");
        }
        scopes.deleteByUser(tenantId, userId);
        scopes.flush();
        List<FuelUserScope> rows = new ArrayList<>();
        for (FuelDivision d : divisions) rows.add(FuelUserScope.division(tenantId, userId, d, by));
        for (UUID depotId : depotSet) rows.add(FuelUserScope.depot(tenantId, userId, depotId, by));
        if (!rows.isEmpty()) scopes.saveAll(rows);
        return new UserScope(userId, divisions.stream().map(Enum::name).sorted().toList(), List.copyOf(depotSet));
    }

    @Transactional(readOnly = true)
    public MyScope mine(TenantId tenantId, UUID userId) {
        Scope s = scopeOf(tenantId, userId);
        return new MyScope(s.restricted(), s.divisions().stream().map(Enum::name).sorted().toList(), List.copyOf(s.depotIds()));
    }
}
