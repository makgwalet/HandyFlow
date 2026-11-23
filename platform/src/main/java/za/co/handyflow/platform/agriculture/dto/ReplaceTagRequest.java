package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

/** The permanent tag number that replaces an animal's temporary one. */
public record ReplaceTagRequest(@NotBlank String tagNumber) {}
