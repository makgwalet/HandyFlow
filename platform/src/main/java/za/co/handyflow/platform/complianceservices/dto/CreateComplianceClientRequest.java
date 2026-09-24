package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateComplianceClientRequest(
        @NotBlank String name, UUID crmCustomerId, String contactEmail, String contactPhone, String mandateNotes
) {}
