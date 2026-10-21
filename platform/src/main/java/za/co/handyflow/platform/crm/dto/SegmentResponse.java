package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

public record SegmentResponse(UUID id, String name, List<SegmentConditionDto> conditions, int count) {}
