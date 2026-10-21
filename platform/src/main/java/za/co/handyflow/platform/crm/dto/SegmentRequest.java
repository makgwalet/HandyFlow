package za.co.handyflow.platform.crm.dto;

import java.util.List;

/** A segment to save or preview. Conditions are all required to match. */
public record SegmentRequest(String name, List<SegmentConditionDto> conditions) {}
