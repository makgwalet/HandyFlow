package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String subject,
        @NotBlank String htmlBody,
                  String previewText,
                  String category
) {}
