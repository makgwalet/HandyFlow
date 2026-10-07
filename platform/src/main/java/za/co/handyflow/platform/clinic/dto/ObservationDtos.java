package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ObservationDtos {
    private ObservationDtos() {}

    /** unit is fixed per code (see ObservationCode) so it is not accepted from the client. */
    public record ObservationRequest(String code, BigDecimal value, BigDecimal refLow, BigDecimal refHigh,
                                     Instant takenAt, UUID consultationId, String notes) {}

    public record ObservationResponse(UUID id, UUID patientId, UUID consultationId, String code,
                                      String label, BigDecimal value, String unit,
                                      BigDecimal refLow, BigDecimal refHigh, String abnormalFlag,
                                      Instant takenAt, UUID takenBy, String source, String status,
                                      String notes) {}
}
