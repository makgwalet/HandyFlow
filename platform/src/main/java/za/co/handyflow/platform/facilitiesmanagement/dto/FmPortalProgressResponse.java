package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.List;

/** The client-facing journey of one work order: where it has got to, step by step. */
public record FmPortalProgressResponse(String status, List<Step> steps) {
    /** state is DONE, CURRENT or PENDING; {@code at} is when it happened (null if not yet or not known). */
    public record Step(String key, String label, String state, Instant at, String note) {}
}
