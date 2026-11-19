package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotNull;

public record PreviewRequest(@NotNull SegmentDefinition definition) {}