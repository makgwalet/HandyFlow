// security/dto/RegisterFirearmRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterFirearmRequest(
        @NotBlank @Size(max = 100) String firearmSerial,
        @NotBlank @Size(max = 50)  String firearmType,
        @Size(max = 150) String makeModel,
        @NotBlank @Size(max = 100) String sapsLicenseNumber,
        LocalDate licenseIssuedAt,
        @NotNull  LocalDate licenseExpiry,
        String notes
) {}
