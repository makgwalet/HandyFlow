package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.SigningMethod;

import java.util.List;

/**
 * Stops a contract that needs a particular signature standard from being signed with a weaker one. A contract with no
 * classification is not blocked (nothing says it needs more); one that requires a method no provider supports is.
 */
@Component
@RequiredArgsConstructor
public class SigningPolicy {

    private final List<SigningProvider> providers;

    public boolean supports(SigningMethod method) {
        return providers.stream().anyMatch(p -> p.method() == method);
    }

    public void assertCanSign(Contract c) {
        String required = c.getRequiredSigningMethod();
        if (required == null || required.isBlank()) return;
        SigningMethod method = SigningMethod.valueOf(required);
        if (!supports(method)) {
            throw new IllegalStateException("This contract requires " + method + " signing, which this system does not support yet. "
                    + "It currently supports simple electronic signing (OTP and drawn signature) only, and that cannot be used as a substitute. "
                    + "Arrange the required signing method outside the system.");
        }
    }
}
