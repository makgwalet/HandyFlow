package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.MemberStatus;

public record CampaignMemberResponse(UUID id, UUID customerId, String name, MemberStatus status, Instant addedAt, Instant statusAt, int stepsDone, boolean exited) {}
