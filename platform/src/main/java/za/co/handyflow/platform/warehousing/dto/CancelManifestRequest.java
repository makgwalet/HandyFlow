package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelManifestRequest(@NotBlank @Size(min = 5, max = 300) String reason) {}
