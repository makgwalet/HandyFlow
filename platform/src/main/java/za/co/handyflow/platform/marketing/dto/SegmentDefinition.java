package za.co.handyflow.platform.marketing.dto;

import java.util.List;

/**
 * Rules for a DYNAMIC segment.
 * match: "ALL" (every condition) or "ANY" (at least one).
 * Condition: field + operator + values (see MarketingAudienceService for the allowed combinations).
 */
public record SegmentDefinition(String match, List<Condition> conditions) {
    public record Condition(String field, String op, List<String> values) {}
}