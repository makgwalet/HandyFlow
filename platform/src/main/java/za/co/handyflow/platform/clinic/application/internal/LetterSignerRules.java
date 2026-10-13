package za.co.handyflow.platform.clinic.application.internal;

import java.util.Collection;
import java.util.UUID;

/**
 * Who may name the signer of a letter (CLINIC-DEC-027, patch 0182). A letter signed by the visit's doctor, or by the practice when there
 * is no visit, needs nothing extra. Naming a practitioner explicitly means signing in that person's name, which needs
 * CLINIC_DOCUMENT_SIGN_AS_OTHER. Pure: no Spring.
 */
public final class LetterSignerRules {

    public static final String PERMISSION = "CLINIC_DOCUMENT_SIGN_AS_OTHER";

    private LetterSignerRules() {}

    public static boolean allowed(UUID chosenSigner, Collection<String> authorities) {
        return chosenSigner == null || (authorities != null && authorities.contains(PERMISSION));
    }
}
