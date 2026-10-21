package za.co.handyflow.platform.catalogue.dto;

import java.util.List;
import java.util.UUID;

/** Suggestions for filling gaps, worked out from the rest of the catalogue. Nothing is changed until someone applies one. */
public record EnrichmentResponse(int scanned, int itemsWithSuggestions, boolean truncated, List<Entry> items) {

    public record Entry(UUID itemId, String name, String sku, List<Suggestion> suggestions) {}

    public record Suggestion(String kind, String value, String label, int confidence, String reason, boolean applicable) {}
}
