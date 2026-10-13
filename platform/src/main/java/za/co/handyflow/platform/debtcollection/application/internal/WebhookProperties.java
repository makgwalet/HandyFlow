package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * handyflow.debtcollection.webhooks.*
 * signing-master-key: server-side secret (at least 32 characters, keep it in your secret store, never in git) from which every endpoint's
 *   signing secret is derived. Without it no endpoint can be created and none can verify a request. Changing it invalidates all secrets.
 * timestamp-tolerance-seconds: how far a request timestamp may differ from server time (replay window).
 * payment-currency: the only currency accepted into the ledger automatically (others are parked in suspense).
 */
@Component
@ConfigurationProperties(prefix = "handyflow.debtcollection.webhooks")
@Getter
@Setter
public class WebhookProperties {
    private String signingMasterKey;
    private long timestampToleranceSeconds = 300;
    private String paymentCurrency = "ZAR";

    public boolean isConfigured() {
        return signingMasterKey != null && signingMasterKey.length() >= 32;
    }
}
