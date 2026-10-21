package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

/** Name people, or a segment (everyone it matches now), or both. */
public record AddMembersRequest(List<UUID> customerIds, UUID segmentId) {}
