package za.co.handyflow.platform.crm.dto;

/** The signing secret is shown once, here. */
public record CreatedWebhookResponse(WebhookResponse webhook, String secret) {}
