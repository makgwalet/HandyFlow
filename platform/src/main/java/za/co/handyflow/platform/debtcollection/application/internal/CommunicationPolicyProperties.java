package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Channel and contact-rule switches, bound from application properties (prefix
 * handyflow.debtcollection.communications). Defaults are deliberately conservative:
 * <ul>
 *   <li>whatsapp-eligibility-confirmed=false — flip to true ONLY after Meta confirms in writing that your
 *   specific collection use is permitted (its Business Messaging Policy lists debt collection as prohibited).</li>
 *   <li>daily-cap-per-case — max non-letter messages per case per day; null/0 = no cap.</li>
 *   <li>quiet-hours-start / quiet-hours-end — local hours (0-23, wraps midnight, e.g. 20 and 8) during which
 *   SMS/WhatsApp collection messages are blocked; both must be set to take effect. The right values are a
 *   legal/compliance decision, so no default is invented here.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "handyflow.debtcollection.communications")
@Getter
@Setter
public class CommunicationPolicyProperties {
    private boolean smsEnabled = true;
    private boolean emailEnabled = true;
    private boolean whatsappEligibilityConfirmed = false;
    private Integer dailyCapPerCase;
    private Integer quietHoursStart;
    private Integer quietHoursEnd;
    private String zone = "Africa/Johannesburg";
}
