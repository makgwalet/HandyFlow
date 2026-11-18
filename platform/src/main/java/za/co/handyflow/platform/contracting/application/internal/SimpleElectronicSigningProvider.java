package za.co.handyflow.platform.contracting.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.SigningMethod;

/** OTP to the signer's phone plus a drawn signature, with IP, time and device recorded. This is not an advanced electronic signature. */
@Component
class SimpleElectronicSigningProvider implements SigningProvider {
    @Override public SigningMethod method() { return SigningMethod.SIMPLE_ELECTRONIC; }
}
