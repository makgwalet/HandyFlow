package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String contractType,
        String description,
        @NotBlank String bodyTemplate,
        Map<String, String> variables,
        String legalArea,
        String requiredSigningMethod   // SIMPLE_ELECTRONIC, ADVANCED_ELECTRONIC, WET_INK, IN_PERSON, or empty if not classified
) {}