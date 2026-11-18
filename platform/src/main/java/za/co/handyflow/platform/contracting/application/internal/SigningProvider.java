package za.co.handyflow.platform.contracting.application.internal;

import za.co.handyflow.platform.contracting.domain.model.SigningMethod;

/**
 * A way of collecting signatures. Each supported {@link SigningMethod} has one provider bean. To add advanced electronic
 * signatures later, add a provider for ADVANCED_ELECTRONIC that integrates an accredited service; contracts that require
 * that method will then be allowed to go out for signing.
 */
public interface SigningProvider {
    SigningMethod method();
}
