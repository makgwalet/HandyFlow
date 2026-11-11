package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupMemberRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Email @Size(max = 254) String email,
        @Size(max = 50) String phone,
        @Size(max = 200) String company,
        @Size(max = 500) String dietaryRequirements
) {}