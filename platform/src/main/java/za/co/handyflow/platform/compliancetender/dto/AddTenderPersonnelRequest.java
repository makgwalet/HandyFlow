package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddTenderPersonnelRequest(@NotNull UUID employeeId, @NotBlank String role) {}
