package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SegmentRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotBlank @Pattern(regexp = "(?i)DYNAMIC|STATIC", message = "type must be DYNAMIC or STATIC") String type,
        @Valid SegmentDefinition definition
) {}