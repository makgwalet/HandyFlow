package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepotRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Code may use letters, digits, dot, dash and underscore") String code,
        @Size(max = 300) String address
) {}
