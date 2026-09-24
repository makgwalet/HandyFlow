package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddClientTenderPersonnelRequest(@NotNull UUID employeeId, @NotBlank String role) {}
