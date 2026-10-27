package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InspectionTemplateRequest(
        @NotBlank @Size(max = 200) String name, @Size(max = 1000) String description, @Size(max = 60) String assetCategory,
        @NotEmpty @Size(max = 100) List<@Valid Item> items
) {
    public record Item(@NotBlank @Size(max = 300) String prompt, boolean critical) {}
}
