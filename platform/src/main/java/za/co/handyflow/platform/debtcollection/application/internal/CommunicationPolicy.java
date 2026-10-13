package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * The gate every outbound message passes before anything is sent. Pure rules over a prepared Input, so it is
 * unit-testable without Spring or a database. Rules run in this order and the first failure wins, with a
 * human-readable reason that is stored on the BLOCKED communication:
 * <ol>
 *   <li>Channel enabled (WhatsApp is off unless eligibility has been confirmed with Meta).</li>
 *   <li>Case status, for collection purposes (REMINDER/DEMAND) only: collection is paused while DISPUTED,
 *   and not driven from this module once HANDED_TO_LEGAL or terminal. Other purposes — e.g. acknowledging a
 *   dispute or a payment — are not blocked by status.</li>
 *   <li>Opt-out: an active do-not-contact entry covering this address and channel.</li>
 *   <li>Quiet hours (SMS/WhatsApp collection messages), when configured.</li>
 *   <li>Daily cap per case (non-letter collection messages), when configured.</li>
 * </ol>
 * These are product guard-rails, not a statement of what the law requires; counsel should set the values.
 */
@Component
@RequiredArgsConstructor
public class CommunicationPolicy {

    private static final Set<CaseStatus> COLLECTABLE =
            EnumSet.of(CaseStatus.OPEN, CaseStatus.DEMAND_SENT, CaseStatus.PAYMENT_PLAN_ACTIVE);

    public record Decision(boolean allowed, String reason) {
        static Decision ok() { return new Decision(true, null); }
        static Decision block(String reason) { return new Decision(false, reason); }
    }

    public record Input(CommunicationChannel channel, CommunicationPurpose purpose, CaseStatus caseStatus,
                        boolean optedOut, LocalTime localTime, long outboundToday) {}

    public record ChannelStatus(CommunicationChannel channel, boolean enabled, String reason) {}

    private final CommunicationPolicyProperties props;

    public Decision evaluate(Input in) {
        ChannelStatus cs = channelStatus(in.channel());
        if (!cs.enabled()) return Decision.block(cs.reason());

        if (in.purpose().isCollection() && !COLLECTABLE.contains(in.caseStatus())) {
            return Decision.block(switch (in.caseStatus()) {
                case DISPUTED -> "Collection messages are paused while the case is DISPUTED — respond to the dispute instead";
                case HANDED_TO_LEGAL -> "Case is HANDED_TO_LEGAL — collection messages are no longer driven from this module";
                default -> "Case is " + in.caseStatus() + " — no further collection messages";
            });
        }
        if (in.optedOut()) {
            return Decision.block("The debtor has opted out of contact on this channel (active opt-out on record)");
        }
        boolean mobileChannel = in.channel() == CommunicationChannel.SMS || in.channel() == CommunicationChannel.WHATSAPP;
        if (in.purpose().isCollection() && mobileChannel && inQuietHours(in.localTime())) {
            return Decision.block("Outside permitted contact hours (quiet hours " + props.getQuietHoursStart()
                    + ":00–" + props.getQuietHoursEnd() + ":00 " + props.getZone() + ")");
        }
        Integer cap = props.getDailyCapPerCase();
        if (in.purpose().isCollection() && cap != null && cap > 0 && in.outboundToday() >= cap) {
            return Decision.block("Daily contact cap reached for this case (" + cap + " per day)");
        }
        return Decision.ok();
    }

    public ChannelStatus channelStatus(CommunicationChannel ch) {
        return switch (ch) {
            case SMS -> new ChannelStatus(ch, props.isSmsEnabled(), props.isSmsEnabled() ? null : "SMS is disabled by configuration");
            case EMAIL -> new ChannelStatus(ch, props.isEmailEnabled(), props.isEmailEnabled() ? null : "Email is disabled by configuration");
            case LETTER -> new ChannelStatus(ch, true, null);
            case WHATSAPP -> props.isWhatsappEligibilityConfirmed()
                    ? new ChannelStatus(ch, true, null)
                    : new ChannelStatus(ch, false, "WhatsApp is not enabled for debt collection: Meta's Business Messaging "
                            + "Policy lists debt collection among prohibited uses. Enable only after Meta confirms "
                            + "eligibility for your specific use (verify the current policy).");
        };
    }

    public Set<CommunicationChannel> enabledChannels() {
        Set<CommunicationChannel> out = EnumSet.noneOf(CommunicationChannel.class);
        for (CommunicationChannel c : CommunicationChannel.values()) if (channelStatus(c).enabled()) out.add(c);
        return out;
    }

    public ZoneId zone() {
        return ZoneId.of(props.getZone());
    }

    boolean inQuietHours(LocalTime t) {
        Integer s = props.getQuietHoursStart();
        Integer e = props.getQuietHoursEnd();
        if (s == null || e == null || s.equals(e)) return false;
        int h = t.getHour();
        return s < e ? (h >= s && h < e) : (h >= s || h < e);
    }
}
