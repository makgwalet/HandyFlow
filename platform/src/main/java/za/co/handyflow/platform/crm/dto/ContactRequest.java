package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 100) String jobTitle,
        @Size(max = 2000) String notes,
        boolean primary   // true makes this the customer's main contact; the first contact is always the main one
) {}
