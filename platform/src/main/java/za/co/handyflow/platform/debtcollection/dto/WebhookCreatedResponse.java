package za.co.handyflow.platform.debtcollection.dto;

/**
 * Returned ONCE when an endpoint is created or its secret rotated — store them now. receiverPath is relative to your
 * API host; the sender must POST to it with the two signature headers described in signatureSpec.
 * token is null on a secret rotation (the URL does not change).
 */
public record WebhookCreatedResponse(WebhookEndpointResponse endpoint, String receiverPath, String token, String secret,
                                     String signatureSpec) {

    public static final String SPEC = "Send X-HandyFlow-Timestamp: <unix epoch seconds> and X-HandyFlow-Signature: "
            + "v1=<lowercase hex HMAC-SHA256(secret, timestamp + \".\" + rawBody)>. Requests older or newer than the "
            + "tolerance window (default 5 minutes) are rejected.";

    public static WebhookCreatedResponse created(WebhookEndpointResponse e, String token, String secret) {
        return new WebhookCreatedResponse(e, "/api/v1/debtcollection/webhooks/" + token, token, secret, SPEC);
    }

    public static WebhookCreatedResponse rotated(WebhookEndpointResponse e, String secret) {
        return new WebhookCreatedResponse(e, null, null, secret, SPEC);
    }
}
