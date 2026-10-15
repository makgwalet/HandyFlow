package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

public record WorkOrderBlocksReleaseRequest(@NotNull Boolean blocksRelease, String reason) {}
