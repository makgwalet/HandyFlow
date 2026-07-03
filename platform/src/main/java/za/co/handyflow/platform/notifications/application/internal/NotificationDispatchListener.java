package za.co.handyflow.platform.notifications.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.channel.EmailChannelSender;
import za.co.handyflow.platform.notifications.application.internal.channel.SmsChannelSender;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.repository.NotificationPreferenceRepository;

/**
 * Delivers EMAIL and SMS for a notification that has already been recorded
 * as IN_APP rows.
 * <p>
 * WHY {@code @TransactionalEventListener(phase = AFTER_COMMIT)} instead of
 * just calling the channel senders directly from NotificationService?
 * Because {@code NotificationService.send()} runs INSIDE the caller's
 * transaction (e.g. inside {@code EarthAssetService.updateStatus}'s
 * {@code @Transactional} method). If we dispatched the email immediately
 * and the surrounding transaction later rolled back — say, a downstream
 * validation failure after the notification call — the user would receive
 * an email about a status change that never actually happened in the
 * database. AFTER_COMMIT guarantees we only ever notify about state that is
 * durably true. This is a common and easy-to-miss bug in systems that fire
 * side effects (emails, webhooks, queue messages) mid-transaction.
 * <p>
 * This listener runs on the calling thread synchronously with respect to
 * the transaction's commit, but the actual channel sends
 * ({@link EmailChannelSender#send}, {@link SmsChannelSender#send}) are
 * themselves {@code @Async}, so this method returns immediately without
 * waiting for SMTP/SMS gateway round-trips.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationDispatchListener {

    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailChannelSender emailChannelSender;
    private final SmsChannelSender smsChannelSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationDispatch(NotificationDispatchEvent event) {
        NotificationRequest request = event.request();

        for (Recipient recipient : request.recipients()) {
            for (NotificationChannel channel : request.channels()) {
                if (channel == NotificationChannel.IN_APP) {
                    continue; // already persisted synchronously by NotificationService
                }
                if (!isEnabled(request, recipient, channel)) {
                    log.debug("Recipient={} has opted out of channel={} — skipping", recipient.userId(), channel);
                    continue;
                }
                dispatch(channel, request, recipient);
            }
        }
    }

    private void dispatch(NotificationChannel channel, NotificationRequest request, Recipient recipient) {
        switch (channel) {
            case EMAIL -> emailChannelSender.send(request, recipient);
            case SMS -> smsChannelSender.send(request, recipient);
            case IN_APP -> { /* no-op, handled above */ }
        }
    }

    /**
     * Platform users can opt out of EMAIL/SMS via their preferences.
     * External (non-user) recipients have no preference record and cannot
     * opt out through this mechanism — the calling module decides whether
     * to notify them at all by including/excluding them as a recipient.
     */
    private boolean isEnabled(NotificationRequest request, Recipient recipient, NotificationChannel channel) {
        if (!recipient.isPlatformUser()) {
            return true;
        }
        return preferenceRepository
                .findForUserAndChannel(request.tenantId(), recipient.userId(), channel.name())
                .map(pref -> pref.isEnabled())
                .orElse(true); // no row = default enabled
    }
}