package za.co.handyflow.platform.debtcollection.domain.model;

/** What an inbound webhook endpoint accepts. One endpoint = one purpose, so a leaked URL can only do that one thing. */
public enum WebhookPurpose { DELIVERY_STATUS, INBOUND_MESSAGE, PAYMENT }
