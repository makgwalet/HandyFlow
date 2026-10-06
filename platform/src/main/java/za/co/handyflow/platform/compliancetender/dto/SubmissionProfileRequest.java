package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** A named submission profile as typed in from a tender's instructions. Every limit is optional; leaving one out means "not stated". */
public record SubmissionProfileRequest(
        @NotBlank @Size(max = 200) String name,
        Set<@Size(max = 10) String> allowedExtensions,
        @Positive Long maxFileBytes,
        @Positive Long maxTotalBytes,
        @Positive Integer maxFileCount,
        Boolean zipAllowed,
        @Positive Integer maxFileNameLength
) {}
