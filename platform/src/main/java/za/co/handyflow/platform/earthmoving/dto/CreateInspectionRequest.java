package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A finished walk-round. {@code inspectionType} is PRE_USE (the default when blank), HANDOVER or RETURN; the last two need a meter reading. Every checklist item must be answered with OK, DEFECT or UNSAFE. */
public record CreateInspectionRequest(@NotNull UUID assetId, BigDecimal meterReading, String notes,
                                      @NotEmpty List<Item> items, String inspectionType) {
    public record Item(String item, String outcome, String comment) {}
}
