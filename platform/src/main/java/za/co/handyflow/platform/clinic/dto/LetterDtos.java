package za.co.handyflow.platform.clinic.dto;

import java.util.List;
import java.util.UUID;

/** Letter templates and the general letter (patch 0164). */
public final class LetterDtos {
    private LetterDtos() {}

    public record TemplateRequest(String kind, String name, String title, String body, String specialty, String urgency, Integer unfitDays) {}

    public record TemplateResponse(UUID id, String kind, String name, String title, String body, String specialty, String urgency, Integer unfitDays) {}

    /** A template with its merge fields filled in for one visit. */
    public record RenderedTemplate(UUID id, String kind, String name, String title, String body, String specialty, String urgency, Integer unfitDays) {}

    public record LetterRequest(String title, String body) {}

    public record MergeFields(List<String> names) {}
}
