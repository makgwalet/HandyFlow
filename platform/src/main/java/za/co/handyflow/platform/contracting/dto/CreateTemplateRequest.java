package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String contractType,
        String description,
        @NotBlank String bodyTemplate,
        Map<String, String> variables
) {}