package za.co.handyflow.platform.compliancetender.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** The saved choices exactly as the screen sent them, and who saved them last. */
public record TenderPackageDraftResponse(JsonNode data, Instant updatedAt, String updatedByName) {}
