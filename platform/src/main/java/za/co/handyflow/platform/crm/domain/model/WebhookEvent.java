package za.co.handyflow.platform.crm.domain.model;

/** What a webhook can be told about. PING is only used by the test button. */
public enum WebhookEvent { LEAD_CREATED, STAGE_CHANGED, OPPORTUNITY_WON, LEAD_CONVERTED, PING }
