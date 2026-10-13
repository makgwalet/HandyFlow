package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookPayloads.DeliveryEvent;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookPayloads.InboundEvent;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookPayloads.PaymentEvent;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookPayloads.PaymentKind;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PtpStatus;
import za.co.handyflow.platform.debtcollection.domain.model.RecoveryEntry;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseItem;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseKind;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookDeliveryLog;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookEndpoint;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PromiseToPayRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.RecoveryEntryRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.SuspenseItemRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.WebhookDeliveryLogRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.WebhookEndpointRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The receiving end of the webhook endpoints: verify, parse, apply — safely and idempotently.
 * <ol>
 *   <li>The URL token selects the endpoint (and tenant + purpose); unknown or revoked tokens get a bare 404.</li>
 *   <li>The HMAC signature and timestamp are verified BEFORE the body is parsed (401 on failure; never a hint why).</li>
 *   <li>Each event is applied on its own; one bad event never blocks the rest of a batch.</li>
 * </ol>
 * HTTP semantics for the sender's retry logic: 200 = everything handled (accepted, parked in suspense, ignored or an
 * already-seen duplicate); 400 = every event was malformed (don't retry); 401 = bad signature; 500 = at least one event
 * hit a transient problem (retry — all handlers are idempotent, so retries are safe).
 * <p>
 * PAYMENTS never guess: an event becomes a ledger payment only when it names exactly one case (caseNumber, or a DC-nnnnn
 * found in payerReference), is in the configured currency and carries a unique reference. Anything else is parked in
 * suspense for a person. A reversal-type status (REVERSED/CHARGEBACK/BOUNCED/RETURNED/REFUNDED, with the ORIGINAL
 * payment's reference) reverses the ledger payment, or dismisses it if it was still in suspense. Non-final statuses
 * (pending, failed, cancelled) are ignored. If the case has exactly one ACTIVE promise to pay, the payment is also filed
 * against it, so the PTP engine sees it.
 * <p>
 * DELIVERY callbacks for an unknown message id are reported as retryable: a fast provider can call back before the
 * send's own bookkeeping has committed. Inbound messages are matched to a case by sender phone/email; zero or several
 * matches go to suspense.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookIngestService {

    public record Outcome(int httpStatus, String message, int accepted, int suspended, int ignored, int duplicates,
                          int invalid, int failed) {
        static Outcome of(int status, String message) { return new Outcome(status, message, 0, 0, 0, 0, 0, 0); }
    }

    private enum Result { ACCEPTED, SUSPENDED, IGNORED, DUPLICATE }

    private static final class RetryLater extends RuntimeException {
        RetryLater(String m) { super(m); }
    }

    private static final Set<CaseStatus> TERMINAL = EnumSet.of(CaseStatus.CLOSED, CaseStatus.WRITTEN_OFF, CaseStatus.SETTLED);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookProperties props;
    private final CommunicationRepository communicationRepository;
    private final CommunicationService communicationService;
    private final DebtCollectionCaseRepository caseRepository;
    private final RecoveryLedgerService ledgerService;
    private final RecoveryEntryRepository ledgerEntries;
    private final SuspenseItemRepository suspenseRepository;
    private final PromiseToPayRepository ptpRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public Outcome handle(String token, String timestamp, String signature, byte[] body) {
        if (!props.isConfigured() || token == null || token.isBlank()) return Outcome.of(404, "Not found");
        WebhookEndpoint ep = endpointRepository.findByTokenHash(WebhookSigner.sha256Hex(token)).orElse(null);
        if (ep == null || !ep.isActive()) return Outcome.of(404, "Not found");

        String secret = WebhookSigner.deriveSecret(props.getSigningMasterKey(), ep.getId(), ep.getSecretVersion());
        if (!WebhookSigner.verify(secret, timestamp, body, signature, Instant.now().getEpochSecond(), props.getTimestampToleranceSeconds())) {
            log(ep, "REJECTED_SIGNATURE", 401, "missing, invalid or stale signature");
            return Outcome.of(401, "Unauthorized");
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception e) {
            log(ep, "BAD_PAYLOAD", 400, "body is not valid JSON");
            return Outcome.of(400, "Body must be JSON");
        }
        List<JsonNode> events = new ArrayList<>();
        if (root != null && root.has("events") && root.get("events").isArray()) root.get("events").forEach(events::add);
        else if (root != null && root.isObject()) events.add(root);
        if (events.isEmpty()) {
            log(ep, "BAD_PAYLOAD", 400, "no events");
            return Outcome.of(400, "No events");
        }

        int accepted = 0, suspended = 0, ignored = 0, dup = 0, invalid = 0, failed = 0;
        for (JsonNode ev : events) {
            try {
                switch (process(ep, ev)) {
                    case ACCEPTED -> accepted++;
                    case SUSPENDED -> suspended++;
                    case IGNORED -> ignored++;
                    case DUPLICATE -> dup++;
                }
            } catch (IllegalArgumentException e) {
                invalid++;
                log.debug("[Debt Collection] webhook {} invalid event: {}", ep.getId(), e.getMessage());
            } catch (Exception e) {
                failed++;
                log.warn("[Debt Collection] webhook {} event failed (retryable): {}", ep.getId(), e.getMessage());
            }
        }
        int status = failed > 0 ? 500 : (invalid == events.size() ? 400 : 200);
        log(ep, failed > 0 ? "PARTIAL_FAILURE" : (invalid == events.size() ? "BAD_PAYLOAD" : "ACCEPTED"), status,
                events.size() + " event(s): " + accepted + " accepted, " + suspended + " suspended, " + ignored + " ignored, "
                        + dup + " duplicate, " + invalid + " invalid, " + failed + " failed");
        try {
            ep.markReceived();
            endpointRepository.save(ep);
        } catch (Exception ignoredEx) {
            // diagnostics only
        }
        return new Outcome(status, status == 200 ? "OK" : status == 400 ? "Invalid event(s)" : "Retry later",
                accepted, suspended, ignored, dup, invalid, failed);
    }

    // ── per purpose ──────────────────────────────────────────────────────────

    private Result process(WebhookEndpoint ep, JsonNode node) {
        return switch (ep.getPurpose()) {
            case DELIVERY_STATUS -> delivery(ep, WebhookPayloads.delivery(node));
            case INBOUND_MESSAGE -> inbound(ep, WebhookPayloads.inbound(node), node);
            case PAYMENT -> payment(ep, WebhookPayloads.payment(node), node);
        };
    }

    private Result delivery(WebhookEndpoint ep, DeliveryEvent e) {
        if (e.status() == null) return Result.IGNORED;
        UUID tenant = ep.getTenantId().getValue();
        List<Communication> matches = communicationRepository.findOutboundByProviderMessageId(e.messageId()).stream()
                .filter(c -> c.getTenantId().getValue().equals(tenant)).toList();
        if (matches.isEmpty()) throw new RetryLater("no message with that id yet");
        if (matches.size() > 1) return Result.IGNORED; // ambiguous ids are never mis-applied
        Communication c = matches.get(0);
        if (!c.applyProviderStatus(e.status(), e.detail(), e.at())) return Result.DUPLICATE;
        communicationRepository.save(c);
        return Result.ACCEPTED;
    }

    private Result inbound(WebhookEndpoint ep, InboundEvent e, JsonNode raw) {
        TenantId t = ep.getTenantId();
        List<DebtCollectionCase> matches = matchCases(t, e.channel(), e.from());
        if (matches.size() == 1) {
            communicationService.recordInbound(t, matches.get(0).getId(), e.channel(), e.from(), e.body(), e.at(),
                    e.messageId(), null, "webhook");
            return Result.ACCEPTED;
        }
        if (e.messageId() != null && suspenseRepository.findByKindAndReference(t, SuspenseKind.MESSAGE, e.messageId()).isPresent()) {
            return Result.DUPLICATE;
        }
        suspenseRepository.save(SuspenseItem.message(t, matches.isEmpty()
                        ? "No open case matches the sender " + e.from() : matches.size() + " open cases match the sender " + e.from(),
                e.messageId(), e.channel(), e.from(), e.body(), e.at() == null ? null : LocalDate.ofInstant(e.at(), java.time.ZoneOffset.UTC),
                raw.toString()));
        return Result.SUSPENDED;
    }

    private Result payment(WebhookEndpoint ep, PaymentEvent e, JsonNode raw) {
        TenantId t = ep.getTenantId();
        if (e.kind() == PaymentKind.IGNORE) return Result.IGNORED;
        if (e.kind() == PaymentKind.REVERSAL) return reversal(t, e);

        if (ledgerEntries.findPaymentByExternalReference(t, e.reference()).isPresent()
                || suspenseRepository.findByKindAndReference(t, SuspenseKind.PAYMENT, e.reference()).isPresent()) {
            return Result.DUPLICATE;
        }
        LocalDate date = e.date() != null ? e.date() : LocalDate.now();
        if (e.currency() == null || !e.currency().equalsIgnoreCase(props.getPaymentCurrency())) {
            return park(t, e, date, raw, e.currency() == null ? "No currency supplied"
                    : "Unsupported currency " + e.currency() + " (expected " + props.getPaymentCurrency() + ")");
        }
        if (e.caseNumber() == null) {
            return park(t, e, date, raw, "No case number in caseNumber or payerReference");
        }
        List<DebtCollectionCase> cases = caseRepository.findAllActive(t).stream()
                .filter(c -> e.caseNumber().equalsIgnoreCase(c.getCaseNumber())).toList();
        if (cases.size() != 1) {
            return park(t, e, date, raw, "Case " + e.caseNumber() + " not found");
        }
        UUID caseId = cases.get(0).getId();
        var active = ptpRepository.findOpenByCaseId(t, caseId).stream().filter(p -> p.getStatus() == PtpStatus.ACTIVE).toList();
        ledgerService.recordPayment(t, caseId, e.amount(), date, e.source(), e.reference(), null,
                active.size() == 1 ? active.get(0).getId() : null, "Webhook payment (" + e.rawStatus() + ")", null, "webhook");
        return Result.ACCEPTED;
    }

    private Result reversal(TenantId t, PaymentEvent e) {
        var entry = ledgerEntries.findPaymentByExternalReference(t, e.reference());
        if (entry.isPresent()) {
            try {
                ledgerService.reversePayment(t, entry.get().getId(), "Provider reported " + e.rawStatus(),
                        e.date() != null ? e.date() : LocalDate.now(), null, "webhook");
                return Result.ACCEPTED;
            } catch (IllegalStateException alreadyReversed) {
                return Result.DUPLICATE;
            }
        }
        var parked = suspenseRepository.findByKindAndReference(t, SuspenseKind.PAYMENT, e.reference());
        if (parked.isPresent() && parked.get().getStatus() == SuspenseStatus.PENDING) {
            SuspenseItem s = parked.get();
            s.dismiss("Payment reported " + e.rawStatus() + " by the provider before it was allocated", null);
            suspenseRepository.save(s);
            return Result.ACCEPTED;
        }
        return Result.IGNORED;
    }

    private Result park(TenantId t, PaymentEvent e, LocalDate date, JsonNode raw, String reason) {
        suspenseRepository.save(SuspenseItem.payment(t, reason, e.reference(), e.amount(), e.currency(), date,
                e.payerReference() != null ? e.payerReference() : e.caseNumber(), e.source().name(), raw.toString()));
        return Result.SUSPENDED;
    }

    private List<DebtCollectionCase> matchCases(TenantId t, CommunicationChannel channel, String from) {
        String wanted;
        try {
            wanted = ContactNormalizer.normalize(from);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        List<DebtCollectionCase> out = new ArrayList<>();
        for (DebtCollectionCase c : caseRepository.findAllActive(t)) {
            if (TERMINAL.contains(c.getStatus())) continue;
            String stored = channel == CommunicationChannel.EMAIL ? c.getDebtorEmail() : c.getDebtorPhone();
            if (stored == null || stored.isBlank()) continue;
            try {
                if (wanted.equals(ContactNormalizer.normalize(stored))) out.add(c);
            } catch (IllegalArgumentException ignored) {
                // unusable stored contact detail: cannot match
            }
        }
        return out;
    }

    private void log(WebhookEndpoint ep, String outcome, int status, String detail) {
        try {
            logRepository.save(WebhookDeliveryLog.of(ep.getTenantId(), ep.getId(), outcome, status, detail));
        } catch (Exception e) {
            log.warn("[Debt Collection] could not write webhook log: {}", e.getMessage());
        }
    }
}
