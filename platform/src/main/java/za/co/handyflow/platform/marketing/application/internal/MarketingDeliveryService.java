package za.co.handyflow.platform.marketing.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.marketing.application.internal.MarketingMailProvider.Kind;
import za.co.handyflow.platform.marketing.application.internal.MarketingMailProvider.OutboundMessage;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.marketing.domain.model.MktCampaignContact;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.marketing.domain.model.MktSendQueue;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignContactRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktContactPreferenceRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktSendQueueRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The delivery engine. Three phases per run, deliberately NOT one big transaction:
 *
 *   1. CLAIM  (tx)    lock a batch (FOR UPDATE SKIP LOCKED), re-check consent + suppression,
 *                     mark eligible items SENDING.
 *   2. SEND   (none)  one synchronous provider call per item. No DB transaction is held open
 *                     during network I/O.
 *   3. RECORD (tx/item) write the outcome: SENT, retry with back-off, or DEAD.
 *
 * Crash safety: if the app dies between phases 1 and 3, the item stays SENDING. We can't know
 * whether the provider accepted it, so after {@code stale-claim-minutes} it is marked FAILED and
 * NOT retried — one lost marketing email is better than a duplicate.
 *
 * Programmatic transactions (TransactionTemplate) are used because this class calls its own
 * methods; @Transactional on those would be bypassed by the Spring proxy.
 */
@Slf4j
@Service
public class MarketingDeliveryService {

    private static final long[] RETRY_BACKOFF_MINUTES = {5, 30, 120};
    private static final int    SYSTEM_FAILURE_BREAKER = 5;
    private static final int    SYSTEM_RETRY_MINUTES   = 10;
    private static final int    MAX_ERROR_LEN          = 250;
    private static final Set<String> COUNTERS = Set.of("sent_count", "bounced_count", "failed_count", "suppressed_count");

    private final MktSendQueueRepository         sendQueueRepo;
    private final MktCampaignContactRepository   contactRepo;
    private final MktContactPreferenceRepository preferenceRepo;
    private final MktCampaignRepository          campaignRepo;
    private final MarketingConsentService        consentService;
    private final MarketingMailProvider          mailProvider;
    private final MarketingService               marketingService;
    private final JdbcTemplate                   jdbc;
    private final TransactionTemplate            tx;

    @Value("${marketing.send.batch-size:50}")         private int  batchSize;
    @Value("${marketing.send.max-retries:3}")         private int  maxRetries;
    @Value("${marketing.send.stale-claim-minutes:15}") private int staleClaimMinutes;
    @Value("${marketing.send.delay-ms:0}")            private long delayMs;
    @Value("${app.api.public-url:https://app.handyflow.co.za}") private String apiPublicUrl;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public MarketingDeliveryService(MktSendQueueRepository sendQueueRepo,
                                    MktCampaignContactRepository contactRepo,
                                    MktContactPreferenceRepository preferenceRepo,
                                    MktCampaignRepository campaignRepo,
                                    MarketingConsentService consentService,
                                    MarketingMailProvider mailProvider,
                                    MarketingService marketingService,
                                    JdbcTemplate jdbc,
                                    PlatformTransactionManager txManager) {
        this.sendQueueRepo   = sendQueueRepo;
        this.contactRepo     = contactRepo;
        this.preferenceRepo  = preferenceRepo;
        this.campaignRepo    = campaignRepo;
        this.consentService  = consentService;
        this.mailProvider    = mailProvider;
        this.marketingService = marketingService;
        this.jdbc            = jdbc;
        this.tx              = new TransactionTemplate(txManager);
    }

    private record Claim(UUID queueId, UUID campaignId, UUID campaignContactId, TenantId tenantId,
                         String to, String toName, String subject, String html,
                         String fromName, String replyTo, String unsubscribeToken, int retryCount) {}

    /** kind == null means success. */
    private record Outcome(Kind kind, String providerMessageId, String error) {
        boolean ok() { return kind == null; }
    }

    // ── Entry point (called by the scheduler; safe on multiple instances) ─────

    public void processQueue() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Delivery run already in progress on this instance — skipping");
            return;
        }
        try {
            recoverStaleClaims();

            List<Claim> claims = tx.execute(status -> claimBatch());
            if (claims != null && !claims.isEmpty()) {
                log.info("Sending {} marketing emails via {}", claims.size(), mailProvider.name());
                sendClaims(claims);
            }
            tx.executeWithoutResult(status -> completeFinishedCampaigns());
        } catch (Exception e) {
            log.error("Delivery run failed", e);
        } finally {
            running.set(false);
        }
    }

    // ── Phase 2 + 3 ───────────────────────────────────────────────────────────

    private void sendClaims(List<Claim> claims) {
        int consecutiveSystemFailures = 0;

        for (int i = 0; i < claims.size(); i++) {
            final Claim claim = claims.get(i);
            final Outcome outcome = send(claim);

            if (outcome.kind() == Kind.SYSTEM) consecutiveSystemFailures++;
            else if (outcome.ok())             consecutiveSystemFailures = 0;

            try {
                tx.executeWithoutResult(status -> record(claim, outcome));
            } catch (Exception e) {
                // Item stays SENDING; stale-claim recovery will close it out. Logged loudly.
                log.error("Could not record outcome for queueId={} to={} (will be recovered as stale): {}",
                        claim.queueId(), claim.to(), e.getMessage(), e);
            }

            if (consecutiveSystemFailures >= SYSTEM_FAILURE_BREAKER) {
                log.error("Aborting delivery run: {} consecutive provider-level failures (last: {}). "
                                + "Releasing {} unsent items.", consecutiveSystemFailures, outcome.error(),
                        claims.size() - i - 1);
                List<Claim> rest = claims.subList(i + 1, claims.size());
                tx.executeWithoutResult(status -> rest.forEach(this::release));
                return;
            }
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    List<Claim> rest = claims.subList(i + 1, claims.size());
                    tx.executeWithoutResult(status -> rest.forEach(this::release));
                    return;
                }
            }
        }
    }

    private Outcome send(Claim c) {
        try {
            String unsubscribeUrl = apiPublicUrl + "/api/v1/marketing/unsubscribe/" + c.unsubscribeToken();
            var result = mailProvider.send(new OutboundMessage(
                    c.campaignContactId(), c.to(), c.toName(), c.subject(), c.html(),
                    c.fromName(), c.replyTo(), unsubscribeUrl));
            return new Outcome(null, result.providerMessageId(), null);
        } catch (MailDeliveryException e) {
            return new Outcome(e.getKind(), null, truncate(e.getMessage()));
        } catch (Exception e) {
            return new Outcome(Kind.TRANSIENT, null, truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private void record(Claim c, Outcome o) {
        if (o.ok()) {
            recordSuccess(c, o.providerMessageId());
            return;
        }
        switch (o.kind()) {
            case RECIPIENT_REJECTED -> recordDead(c, o.error(), c.retryCount(), true);
            case PERMANENT          -> recordDead(c, o.error(), c.retryCount(), false);
            case SYSTEM             -> requeue(c, c.retryCount(), SYSTEM_RETRY_MINUTES, o.error());
            case TRANSIENT -> {
                int attempts = c.retryCount() + 1;
                if (attempts >= maxRetries) {
                    recordDead(c, "Gave up after " + attempts + " attempts: " + o.error(), attempts, false);
                } else {
                    long minutes = RETRY_BACKOFF_MINUTES[Math.min(attempts - 1, RETRY_BACKOFF_MINUTES.length - 1)];
                    requeue(c, attempts, minutes, o.error());
                }
            }
        }
    }

    private void recordSuccess(Claim c, String providerMessageId) {
        jdbc.update("UPDATE mkt_send_queue SET status = 'SENT', provider_message_id = ?, "
                + "processed_at = now(), locked_at = NULL WHERE id = ?", providerMessageId, c.queueId());
        // The mail HAS gone out, so sent_count is bumped regardless of what happened to the row meanwhile.
        jdbc.update("""
            UPDATE mkt_campaign_contacts
            SET status = CASE WHEN status = 'PENDING' THEN 'SENT' ELSE status END,
                sent_at = now(),
                delivery_status = CASE WHEN delivery_status = 'QUEUED' THEN 'ACCEPTED' ELSE delivery_status END,
                provider_message_id = ?
            WHERE id = ?
            """, providerMessageId, c.campaignContactId());
        bump(c.campaignId(), "sent_count");
    }

    private void requeue(Claim c, int retryCount, long minutes, String error) {
        jdbc.update("""
            UPDATE mkt_send_queue
            SET status = 'PENDING', locked_at = NULL, retry_count = ?, error_message = ?,
                processed_at = now(), scheduled_at = now() + make_interval(mins => ?)
            WHERE id = ?
            """, retryCount, error, (int) minutes, c.queueId());
        log.warn("Send to={} will retry in {} min (attempt {}): {}", c.to(), minutes, retryCount, error);
    }

    /** Unsent items from an aborted run go back to the queue without using a retry. */
    private void release(Claim c) {
        jdbc.update("""
            UPDATE mkt_send_queue
            SET status = 'PENDING', locked_at = NULL, scheduled_at = now() + make_interval(mins => ?)
            WHERE id = ? AND status = 'SENDING'
            """, SYSTEM_RETRY_MINUTES, c.queueId());
    }

    private void recordDead(Claim c, String error, int retryCount, boolean recipientRejected) {
        jdbc.update("UPDATE mkt_send_queue SET status = 'DEAD', error_message = ?, retry_count = ?, "
                + "processed_at = now(), locked_at = NULL WHERE id = ?", error, retryCount, c.queueId());

        if (recipientRejected) {
            int n = jdbc.update("""
                UPDATE mkt_campaign_contacts
                SET status = 'BOUNCED', delivery_status = 'BOUNCED', bounced_at = now(), error_message = ?
                WHERE id = ? AND status = 'PENDING'
                """, error, c.campaignContactId());
            if (n > 0) bump(c.campaignId(), "bounced_count");
            // A synchronous 5.1.x rejection is a hard bounce: suppress so we never try this address again.
            marketingService.applyDeliverySuppression(c.tenantId(), c.to(),
                    "HARD_BOUNCE", "SMTP_REJECTED", error);
            log.warn("Hard bounce (SMTP rejected) to={} campaign={}: {}", c.to(), c.campaignId(), error);
        } else {
            int n = jdbc.update("""
                UPDATE mkt_campaign_contacts
                SET status = 'FAILED', delivery_status = 'FAILED', error_message = ?
                WHERE id = ? AND status = 'PENDING'
                """, error, c.campaignContactId());
            if (n > 0) bump(c.campaignId(), "failed_count");
            log.warn("Send failed permanently to={} campaign={}: {}", c.to(), c.campaignId(), error);
        }
    }

    // ── Phase 1: claim ────────────────────────────────────────────────────────

    private List<Claim> claimBatch() {
        List<MktSendQueue> batch = sendQueueRepo.findPendingBatch(Instant.now(), PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return List.of();

        Map<UUID, MktCampaignContact> contacts = contactRepo
                .findAllById(batch.stream().map(MktSendQueue::getCampaignContactId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MktCampaignContact::getId, c -> c));

        Map<UUID, MktContactPreference> prefs = preferenceRepo
                .findAllById(contacts.values().stream().map(MktCampaignContact::getPreferenceId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MktContactPreference::getId, p -> p));

        Map<UUID, MktCampaign> campaigns = campaignRepo
                .findAllById(batch.stream().map(MktSendQueue::getCampaignId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MktCampaign::getId, c -> c));

        Map<UUID, Set<String>> suppressedByTenant = new HashMap<>();
        batch.stream()
                .collect(Collectors.groupingBy(MktSendQueue::getTenantId,
                        Collectors.mapping(MktSendQueue::getToEmail, Collectors.toList())))
                .forEach((tenantUuid, emails) -> suppressedByTenant.put(
                        tenantUuid, consentService.activeSuppressedAmong(TenantId.of(tenantUuid), emails)));

        List<Claim> claims = new ArrayList<>();
        for (MktSendQueue item : batch) {
            MktCampaignContact cc = contacts.get(item.getCampaignContactId());
            MktCampaign campaign  = campaigns.get(item.getCampaignId());

            // Already handled, or the rows are gone — never send twice.
            if (cc == null || !cc.isPending() || campaign == null) {
                item.markSuppressed("Recipient or campaign record missing, or already processed");
                continue;
            }

            MktContactPreference pref = cc.getPreferenceId() != null ? prefs.get(cc.getPreferenceId()) : null;
            var decision = consentService.evaluate(pref, item.getToEmail(),
                    suppressedByTenant.getOrDefault(item.getTenantId(), Set.of()));

            // Send-time eligibility re-check — the last gate before an email leaves.
            if (!decision.eligible()) {
                item.markSuppressed("SUPPRESSED: " + decision.reason());
                cc.markSuppressed(decision.reason());
                bump(item.getCampaignId(), "suppressed_count");
                continue;
            }

            item.markClaimed();
            claims.add(new Claim(item.getId(), item.getCampaignId(), item.getCampaignContactId(),
                    TenantId.of(item.getTenantId()), item.getToEmail(), item.getToName(),
                    item.getSubject(), item.getHtmlBody(),
                    campaign.getFromName(), campaign.getReplyTo(),
                    pref.getUnsubscribeToken(), item.getRetryCount()));
        }

        sendQueueRepo.saveAll(batch);
        contactRepo.saveAll(contacts.values());
        return claims;
    }

    // ── Housekeeping ──────────────────────────────────────────────────────────

    private void recoverStaleClaims() {
        try {
            tx.executeWithoutResult(status -> {
                List<Map<String, Object>> stale = jdbc.queryForList("""
                    UPDATE mkt_send_queue
                    SET status = 'DEAD', processed_at = now(), locked_at = NULL,
                        error_message = 'Outcome unknown after an interruption during sending - not retried, to avoid a duplicate email'
                    WHERE status = 'SENDING' AND locked_at < now() - make_interval(mins => ?)
                    RETURNING campaign_id, campaign_contact_id
                    """, staleClaimMinutes);
                for (Map<String, Object> row : stale) {
                    UUID campaignId = (UUID) row.get("campaign_id");
                    int n = jdbc.update("""
                        UPDATE mkt_campaign_contacts
                        SET status = 'FAILED', delivery_status = 'FAILED',
                            error_message = 'Outcome unknown after an interruption during sending'
                        WHERE id = ? AND status = 'PENDING'
                        """, row.get("campaign_contact_id"));
                    if (n > 0) bump(campaignId, "failed_count");
                }
                if (!stale.isEmpty()) {
                    log.error("{} send(s) were interrupted mid-flight and closed out as FAILED (not retried)", stale.size());
                }
            });
        } catch (Exception e) {
            log.error("Stale-claim recovery failed", e);
        }
    }

    /** SENDING campaigns with nothing left PENDING or SENDING are done. */
    private void completeFinishedCampaigns() {
        int done = jdbc.update("""
            UPDATE mkt_campaigns c
            SET status = 'SENT', sent_at = now(), updated_at = now(), version = COALESCE(version, 0) + 1
            WHERE c.status = 'SENDING'
              AND c.recipient_count > 0
              AND NOT EXISTS (SELECT 1 FROM mkt_send_queue q
                              WHERE q.campaign_id = c.id AND q.status IN ('PENDING', 'SENDING'))
            """);
        if (done > 0) log.info("{} campaign(s) fully processed — marked SENT", done);
    }

    private void bump(UUID campaignId, String column) {
        if (!COUNTERS.contains(column)) throw new IllegalArgumentException("Unsupported counter " + column);
        jdbc.update("UPDATE mkt_campaigns SET " + column + " = " + column + " + 1, updated_at = now() WHERE id = ?",
                campaignId);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_ERROR_LEN ? s.substring(0, MAX_ERROR_LEN) : s;
    }
}