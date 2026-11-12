package za.co.handyflow.platform.fuel.dto;

import java.util.List;
import java.util.UUID;

/** Phase 8c: limits on a user's Fuel access. Empty lists mean no limit of that kind; both empty means unrestricted. */
public final class ScopeDtos {
    private ScopeDtos() {}

    /** Replaces the user's whole set. divisions are FuelDivision names; null is treated as empty. */
    public record SaveScopeRequest(List<String> divisions, List<UUID> depotIds) {}

    public record UserScope(UUID userId, List<String> divisions, List<UUID> depotIds) {}

    /** The caller's own effective limits, so the screens can hide what the server would refuse. */
    public record MyScope(boolean restricted, List<String> divisions, List<UUID> depotIds) {}
}
