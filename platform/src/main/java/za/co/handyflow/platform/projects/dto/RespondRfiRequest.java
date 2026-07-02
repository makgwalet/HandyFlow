package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;

public record RespondRfiRequest(

        @NotBlank(message = "Response text is required")
        @Size(max = 10000, message = "Response must not exceed 10 000 characters")
        String response

) {}
