package za.co.handyflow.platform.clinic.dto;

import java.util.List;

/** Which question groups a visit type opens, in order. */
public final class VisitMappingDtos {
    private VisitMappingDtos() {}

    public record Entry(String groupCode, boolean required) {}

    /** source is TENANT when this practice has set its own list, PLATFORM when the platform default applies. */
    public record Mapping(String visitType, String source, List<Entry> groups) {}

    public record SetMappingRequest(List<Entry> groups) {}
}
