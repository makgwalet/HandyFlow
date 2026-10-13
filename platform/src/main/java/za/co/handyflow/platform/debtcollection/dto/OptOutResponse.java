package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationOptOut;

import java.time.Instant;
import java.util.UUID;

public record OptOutResponse(UUID id, String contactValue, CommunicationChannel channel, String reason, String source,
                             String recordedByUserName, Instant createdAt, Instant revokedAt, String revokeReason) {
    public static OptOutResponse of(CommunicationOptOut o) {
        return new OptOutResponse(o.getId(), o.getContactValue(), o.getChannel(), o.getReason(), o.getSource(),
                o.getRecordedByUserName(), o.getCreatedAt(), o.getRevokedAt(), o.getRevokeReason());
    }
}
