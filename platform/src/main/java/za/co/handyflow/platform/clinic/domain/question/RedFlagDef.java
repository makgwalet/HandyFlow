package za.co.handyflow.platform.clinic.domain.question;

import java.util.Map;

/**
 * A red flag is a prompt to the clinician ("this answer needs attention"), kept apart from ordinary rules
 * (Q-5). It is a flag, never a diagnosis. severity is INFO or URGENT.
 */
public record RedFlagDef(String code, String label, String severity, Map<String, Object> expression, String message) {}
