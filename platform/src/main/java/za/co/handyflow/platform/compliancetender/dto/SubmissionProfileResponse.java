package za.co.handyflow.platform.compliancetender.dto;

import java.util.Set;
import java.util.UUID;

public record SubmissionProfileResponse(
        UUID id, String name, Set<String> allowedExtensions, Long maxFileBytes, Long maxTotalBytes,
        Integer maxFileCount, Boolean zipAllowed, Integer maxFileNameLength
) {}
