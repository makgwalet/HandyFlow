package za.co.handyflow.platform.marketing.dto;

import java.util.List;
import java.util.UUID;

public record AddSegmentMembersRequest(List<String> emails, List<UUID> preferenceIds) {}