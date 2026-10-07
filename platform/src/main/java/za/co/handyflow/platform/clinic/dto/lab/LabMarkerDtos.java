package za.co.handyflow.platform.clinic.dto.lab;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabMarkerDtos {
    private LabMarkerDtos() {}

    /**
     * One line copied from the lab report. The reference range and critical limits are the LAB'S, as printed on the
     * report; the system holds none of its own. flag is optional (NORMAL, LOW, HIGH, ABNORMAL, CRITICAL) and is
     * how a clinician marks a text result such as "Positive" or escalates a number.
     */
    public record MarkerInput(String marker, String value, String unit,
                              BigDecimal refLow, BigDecimal refHigh,
                              BigDecimal criticalLow, BigDecimal criticalHigh,
                              String flag) {}

    /** The whole list of markers for the result; replaces what was there. An empty list clears them. */
    public record SaveMarkersRequest(@NotNull List<MarkerInput> markers) {}

    /** A result with at least one critical marker that no clinician has reviewed yet. */
    public record CriticalLabItem(UUID id, UUID patientId, String patientName, String patientNameRaw,
                                  String labReference, Instant receivedAt, String criticalMarkers) {}
}
