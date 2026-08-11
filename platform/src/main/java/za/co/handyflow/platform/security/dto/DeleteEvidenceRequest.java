// security/dto/DeleteEvidenceRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteEvidenceRequest(
        @NotBlank String reason
) {}