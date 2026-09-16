package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePostRequest(@NotBlank String name, String description) {}
