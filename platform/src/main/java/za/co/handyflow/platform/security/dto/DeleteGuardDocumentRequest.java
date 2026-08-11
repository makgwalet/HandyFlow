// security/dto/DeleteGuardDocumentRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteGuardDocumentRequest(
        @NotBlank String reason
) {}