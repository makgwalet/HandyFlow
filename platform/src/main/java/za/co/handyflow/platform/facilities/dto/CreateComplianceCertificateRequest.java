package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateComplianceCertificateRequest(
        @NotNull UUID siteId, UUID assetId, @NotBlank String certificateType, String certificateNumber,
        String issuedBy, @NotNull LocalDate issueDate, @NotNull LocalDate expiryDate, String documentRef
) {}
