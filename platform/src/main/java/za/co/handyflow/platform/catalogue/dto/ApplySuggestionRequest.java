package za.co.handyflow.platform.catalogue.dto;

/** {@code kind} is CATEGORY, BRAND or SKU; {@code value} is the suggestion's value (a category id, a brand or a SKU). */
public record ApplySuggestionRequest(String kind, String value) {}
