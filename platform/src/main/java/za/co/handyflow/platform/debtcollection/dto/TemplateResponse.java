package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationTemplate;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(UUID id, String code, int templateVersion, CommunicationChannel channel, String subject,
                               String body, boolean latest, Instant createdAt) {
    public static TemplateResponse of(CommunicationTemplate t) {
        return new TemplateResponse(t.getId(), t.getCode(), t.getTemplateVersion(), t.getChannel(), t.getSubject(),
                t.getBody(), t.isLatest(), t.getCreatedAt());
    }
}
