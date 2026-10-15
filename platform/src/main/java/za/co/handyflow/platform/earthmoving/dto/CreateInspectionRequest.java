package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A finished walk-round. {@code inspectionType} is PRE_USE (the default when blank), HANDOVER, RETURN, MAINTENANCE or SAFETY; handover and return need a
 * meter reading. {@code templateVersionId} is the checklist version the person had open; blank means the one in force. Each item is answered
 * according to its question: OK, DEFECT or UNSAFE for a check, {@code valueNumber} for a measurement, {@code valueText} for a note, {@code evidenceRef} for a photo.
 */
public record CreateInspectionRequest(@NotNull UUID assetId, BigDecimal meterReading, String notes,
                                      @NotEmpty List<Item> items, String inspectionType, UUID templateVersionId, String signOffName) {
    public CreateInspectionRequest(UUID assetId, BigDecimal meterReading, String notes, List<Item> items, String inspectionType) {
        this(assetId, meterReading, notes, items, inspectionType, null, null);
    }

    public record Item(String item, String outcome, String comment, BigDecimal valueNumber, String valueText, String evidenceRef) {
        public Item(String item, String outcome, String comment) { this(item, outcome, comment, null, null, null); }
    }
}
