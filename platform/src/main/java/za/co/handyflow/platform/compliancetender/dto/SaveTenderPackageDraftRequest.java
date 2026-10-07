package za.co.handyflow.platform.compliancetender.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SaveTenderPackageDraftRequest(@NotNull JsonNode data) {}
