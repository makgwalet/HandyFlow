package za.co.handyflow.platform.events.application.internal.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Finds the adapter for a provider code, and the one configured as the default for new checkouts. */
@Component
public class PaymentGateways {

    private final Map<String, PaymentGateway> byProvider = new HashMap<>();

    @Value("${app.events.payments.provider:MANUAL}")
    private String defaultProvider;

    public PaymentGateways(List<PaymentGateway> gateways) {
        for (PaymentGateway g : gateways) {
            byProvider.put(g.provider().toUpperCase(Locale.ROOT), g);
        }
    }

    public Optional<PaymentGateway> find(String provider) {
        return provider == null ? Optional.empty() : Optional.ofNullable(byProvider.get(provider.toUpperCase(Locale.ROOT)));
    }

    /** The provider new checkouts use. Fails clearly if configuration names one that isn't installed. */
    public PaymentGateway defaultGateway() {
        return find(defaultProvider).orElseThrow(() -> new HandyFlowException(
                "The configured payment provider '" + defaultProvider + "' is not available",
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE"));
    }
}