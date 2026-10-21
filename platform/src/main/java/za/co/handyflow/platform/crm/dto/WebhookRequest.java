package za.co.handyflow.platform.crm.dto;

import java.util.List;
import za.co.handyflow.platform.crm.domain.model.WebhookEvent;

public record WebhookRequest(String name, String url, List<WebhookEvent> events, Boolean active) {}
