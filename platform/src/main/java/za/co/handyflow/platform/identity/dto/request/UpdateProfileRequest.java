package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
                  String phone,
                  String jobTitle,
                  String department
) {}
