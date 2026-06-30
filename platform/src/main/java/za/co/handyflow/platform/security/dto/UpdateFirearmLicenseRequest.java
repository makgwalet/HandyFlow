package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateFirearmLicenseRequest(
        @NotBlank @Size(max = 100) String sapsLicenseNumber,
        LocalDate licenseIssuedAt,
        @NotNull LocalDate licenseExpiry
) {}
