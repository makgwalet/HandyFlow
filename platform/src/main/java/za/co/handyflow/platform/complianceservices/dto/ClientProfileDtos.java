package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ClientProfileDtos {

    private ClientProfileDtos() {}

    public record SaveProfileRequest(
            @Size(max = 255) String legalName, @Size(max = 255) String tradingName,
            @Size(max = 50) String registrationNumber, @Size(max = 30) String entityType,
            @Size(max = 30) String taxNumber, @Size(max = 30) String vatNumber,
            @Size(max = 1000) String registeredAddress, @Size(max = 1000) String postalAddress,
            @Size(max = 120) String industry, @Min(0) @Max(10_000_000) Integer employeeCount,
            @Size(max = 30) String turnoverBand, @Size(max = 10) String riskRating,
            @Size(max = 20) String onboardingStatus) {}

    public record SaveSourceRequest(@Size(max = 20) String source, @Size(max = 200) String method,
                                    @Size(max = 200) String reference, LocalDate sourceDate) {}

    public record ConfirmSourceRequest(@Size(max = 1000) String exceptionReason) {}

    public record FieldSourceView(String fieldName, String source, String method, String reference, LocalDate sourceDate,
                                  UUID recordedBy, Instant recordedAt, boolean verified, UUID verifiedBy, Instant verifiedAt,
                                  boolean confirmedByEnterer, String exceptionReason) {}

    public record ProfileResponse(
            UUID clientId, String legalName, String tradingName, String registrationNumber, String entityType,
            String taxNumber, String vatNumber, String registeredAddress, String postalAddress, String industry,
            Integer employeeCount, String turnoverBand, String riskRating, String onboardingStatus,
            List<FieldSourceView> sources, List<String> unconfirmedFields) {}
}
