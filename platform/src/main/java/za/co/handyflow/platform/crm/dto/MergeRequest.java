package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

/** keepId stays; mergeId is folded into it and removed. take lists the fields where the merged record's value is wanted instead. */
public record MergeRequest(UUID keepId, UUID mergeId, List<String> take) {}
