package za.co.handyflow.platform.fuel.dto;

import za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ArrivalCheckDtos {
    private ArrivalCheckDtos() {}

    /** @param allowed false when anything blocks. Review points do not block here: the driver sees them and the office decides. */
    public record ArrivalCheckView(UUID deliveryId, boolean allowed, List<Finding> findings, Instant checkedAt) {}
}
