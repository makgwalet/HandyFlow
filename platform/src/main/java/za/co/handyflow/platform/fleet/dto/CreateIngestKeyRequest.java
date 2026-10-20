package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIngestKeyRequest(
        @NotBlank @Size(max = 150) String name     // e.g. "Cartrack production"
) {}