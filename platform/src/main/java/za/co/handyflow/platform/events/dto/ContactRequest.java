package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank String contactType,       // MEDICAL, FIRE, POLICE, SECURITY, VENUE, ORGANISER, SUPPLIER, OTHER
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String organisation,
        @NotBlank @Size(max = 50) String phone,
        @Size(max = 50) String alternatePhone,
        @Size(max = 254) String email,
        @Size(max = 500) String notes
) {}