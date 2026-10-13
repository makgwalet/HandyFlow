package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.debtcollection.application.internal.CollectionMessageSender.OutboundMessage;
import za.co.handyflow.platform.debtcollection.application.internal.CollectionMessageSender.SendResult;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationPolicy.Decision;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationDirection;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationTemplate;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.LetterDispatchMethod;
import za.co.handyflow.platform.debtcollection.domain.model.PtpEvent;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.repository.CaseTimelineRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.CollectionContactLogRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Case communications: policy-gated sending, inbound capture, letter dispatch records, delivery status and the
 * unified timeline (communications + the manual contact log + PTP events).
 * <p>
 * SEND FLOW. send() is deliberately NOT one transaction: (1) validate and persist the message as QUEUED (or
 * BLOCKED, with the policy reason) and commit; (2) call the sender adapter OUTSIDE any transaction so a slow
 * provider never holds a DB connection and a rollback can never "unsend" a message; (3) in a fresh transaction
 * record SENT or FAILED; (4) best-effort side effects (CRM activity log) each in their own transaction so their
 * failure cannot corrupt the send record. Known gap: a crash between (2) and (3) leaves a QUEUED row for a
 * message that may have gone out — provider delivery callbacks (applyProviderStatus) or a stale-QUEUED review
 * reconcile it. An optional idempotencyKey makes client retries safe.
 * <p>
 * BLOCKED is not an error: the attempt is stored with its reason (part of the compliance trail) and the response
 * lists enabled alternative channels, so a workflow can fall back explicitly rather than failing silently.
 * <p>
 * Letters are a record of something staff already did, so recordLetter() is never blocked by policy.
 * Content sent by template is rendered from the case's stored totalOutstanding snapshot — refresh it first if
 * the figure must be current. CRM logging assumes CrmFacade.logCommunication() accepts direction "INBOUND";
 * that direction value has not been verified against CrmFacade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationService {

    public record SendCommand(CommunicationChannel channel, CommunicationPurpose purpose, String recipient,
                              String templateCode, String subject, String body, Map<String, String> variables,
                              String processingBasis, String idempotencyKey) {}

    public record SendOutcome(Communication communication, Set<CommunicationChannel> suggestedAlternatives) {}

    public record TimelineEntry(String kind, Instant at, String channel, String direction, String summary,
                                String status, UUID refId) {}

    private record Content(String subject, String body, String templateCode, Integer templateVersion) {}

    private final CommunicationRepository repository;
    private final DebtCollectionCaseService caseService;
    private final CollectionContactLogRepository contactLogRepository;
    private final CaseTimelineRepository timelineRepository;
    private final CommunicationTemplateService templateService;
    private final CommunicationOptOutService optOutService;
    private final CommunicationPolicy policy;
    private final ObjectProvider<CollectionMessageSender> senders;
    private final CrmFacade crmFacade;
    private final WorkItemService workItemService;
    private final TransactionTemplate tx;

    // ── outbound ─────────────────────────────────────────────────────────────

    public SendOutcome send(TenantId tenantId, UUID caseId, SendCommand cmd, UUID actorId, String actorName) {
        if (cmd.channel() == CommunicationChannel.LETTER) {
            throw new IllegalArgumentException("Letters are recorded with the letters endpoint — the system does not post them");
        }
        if (cmd.idempotencyKey() != null) {
            var existing = repository.findByIdempotencyKey(tenantId, cmd.idempotencyKey());
            if (existing.isPresent()) return outcome(existing.get());
        }
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        Content content = resolveContent(tenantId, c, cmd);
        String to = resolveRecipient(c, cmd);
        String normalized = ContactNormalizer.normalize(to);

        ZoneId zone = policy.zone();
        Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Decision decision = policy.evaluate(new CommunicationPolicy.Input(cmd.channel(), cmd.purpose(), c.getStatus(),
                optOutService.isOptedOut(tenantId, normalized, cmd.channel()), LocalTime.now(zone),
                repository.countOutboundSince(tenantId, caseId, startOfDay)));

        Communication queued;
        try {
            queued = tx.execute(s -> {
                Communication comm = Communication.outbound(tenantId, caseId, cmd.channel(), cmd.purpose(), to,
                        content.subject(), content.body(), content.templateCode(), content.templateVersion(),
                        cmd.processingBasis(), cmd.idempotencyKey(), actorId, actorName);
                if (!decision.allowed()) comm.markBlocked(decision.reason());
                return repository.save(comm);
            });
        } catch (DataIntegrityViolationException e) {
            if (cmd.idempotencyKey() != null) { // lost a race with an identical retry
                var existing = repository.findByIdempotencyKey(tenantId, cmd.idempotencyKey());
                if (existing.isPresent()) return outcome(existing.get());
            }
            throw e;
        }
        if (queued.getStatus() == CommunicationStatus.BLOCKED) return outcome(queued);

        SendResult result = transmit(tenantId, queued, to, content);
        UUID id = queued.getId();
        Communication done = tx.execute(s -> {
            Communication comm = repository.findById(id).orElseThrow();
            if (result.accepted()) {
                comm.markSent(result.providerMessageId(), Instant.now());
                caseService.recordContact(tenantId, caseId, LocalDate.now(zone));
            } else {
                comm.markFailed(result.error());
            }
            return repository.save(comm);
        });
        if (result.accepted()) crmLog(tenantId, c, done, actorId);
        return outcome(done);
    }

    /** Records a letter staff have dispatched (post, registered post, courier, by hand). Never blocked: it is a record of fact. */
    public Communication recordLetter(TenantId tenantId, UUID caseId, CommunicationPurpose purpose, String postalAddress,
                                      String subject, String summary, LetterDispatchMethod method,
                                      String trackingReference, UUID evidenceId, Instant sentAt, String templateCode,
                                      UUID actorId, String actorName) {
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        Integer tplVersion = null;
        if (templateCode != null && !templateCode.isBlank()) {
            CommunicationTemplate tpl = templateService.latest(tenantId, templateCode);
            if (tpl.getChannel() != CommunicationChannel.LETTER) {
                throw new IllegalArgumentException("Template " + tpl.getCode() + " is for " + tpl.getChannel() + ", not LETTER");
            }
            tplVersion = tpl.getTemplateVersion();
        }
        final Integer version = tplVersion;
        ZoneId zone = policy.zone();
        Communication comm = tx.execute(s -> {
            Communication l = repository.save(Communication.letterSent(tenantId, caseId, purpose, postalAddress, subject,
                    summary, method, trackingReference, evidenceId, sentAt, templateCode, version, actorId, actorName));
            caseService.recordContact(tenantId, caseId, LocalDate.now(zone));
            return l;
        });
        crmLog(tenantId, c, comm, actorId);
        return comm;
    }

    // ── inbound ──────────────────────────────────────────────────────────────

    /**
     * Captures a debtor's reply. Idempotent on providerMessageId. An SMS/WhatsApp whose whole body is an opt-out
     * keyword also records a channel opt-out. A review work item is raised so a collector sees the reply in My Work.
     */
    public Communication recordInbound(TenantId tenantId, UUID caseId, CommunicationChannel channel, String from,
                                       String body, Instant receivedAt, String providerMessageId,
                                       UUID actorId, String actorName) {
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        if (providerMessageId != null) {
            var dup = repository.findInboundByProviderMessageId(tenantId, providerMessageId);
            if (dup.isPresent()) return dup.get();
        }
        ZoneId zone = policy.zone();
        boolean optOut = (channel == CommunicationChannel.SMS || channel == CommunicationChannel.WHATSAPP)
                && CommunicationOptOutService.isOptOutKeyword(body);
        Communication comm = tx.execute(s -> {
            Communication in = repository.save(Communication.inbound(tenantId, caseId, channel, from, body, receivedAt,
                    providerMessageId, actorId, actorName));
            caseService.recordContact(tenantId, caseId, LocalDate.ofInstant(in.getReceivedAt(), zone));
            if (optOut) {
                optOutService.record(tenantId, from, channel, "Debtor replied with an opt-out keyword", "INBOUND_KEYWORD",
                        actorId, actorName);
            }
            return in;
        });
        crmLog(tenantId, c, comm, actorId);
        if (c.getStatus() != CaseStatus.CLOSED) {
            String title = optOut
                    ? "Debtor opted out of " + channel + " — review contact strategy for " + c.getCaseNumber()
                    : "Debtor replied by " + channel + " — review and respond (" + c.getCaseNumber() + ")";
            bestEffort("raise inbound work item", () -> workItemService.raise(tenantId, caseId, WorkItemType.FOLLOW_UP_DUE,
                    title, LocalDate.now(zone), "INBOUND:" + comm.getId(), null, "system"));
        }
        return comm;
    }

    // ── status ───────────────────────────────────────────────────────────────

    /** Provider callback entry point (no tenant on the call). Ambiguous or unknown ids are ignored, never mis-applied. */
    @Transactional
    public boolean applyProviderStatus(String providerMessageId, CommunicationStatus status, String detail, Instant at) {
        List<Communication> matches = repository.findOutboundByProviderMessageId(providerMessageId);
        if (matches.size() != 1) {
            log.warn("[Debt Collection] provider status ignored: providerMessageId={} matched {} messages",
                    providerMessageId, matches.size());
            return false;
        }
        Communication c = matches.get(0);
        boolean changed = c.applyProviderStatus(status, detail, at);
        if (changed) repository.save(c);
        return changed;
    }

    /** Staff update of a letter's outcome (DELIVERED or RETURNED, e.g. uncollected registered post). */
    @Transactional
    public Communication updateLetterStatus(TenantId tenantId, UUID id, CommunicationStatus status, String detail) {
        Communication c = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Communication", id.toString()));
        if (c.getChannel() != CommunicationChannel.LETTER) {
            throw new IllegalArgumentException("Only letter status is updated manually; other channels update from provider callbacks");
        }
        if (status != CommunicationStatus.DELIVERED && status != CommunicationStatus.RETURNED) {
            throw new IllegalArgumentException("A letter can be marked DELIVERED or RETURNED");
        }
        if (!c.applyProviderStatus(status, detail, Instant.now())) {
            throw new IllegalStateException("Cannot move a " + c.getStatus() + " letter to " + status);
        }
        return repository.save(c);
    }

    // ── queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Communication> listForCase(TenantId tenantId, UUID caseId) {
        caseService.get(tenantId, caseId);
        return repository.findByCaseId(tenantId, caseId);
    }

    /** Newest first: messages, manual contact-log entries (calls etc.) and PTP events for one case. */
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(TenantId tenantId, UUID caseId, int limit) {
        caseService.get(tenantId, caseId);
        List<TimelineEntry> out = new ArrayList<>();
        for (Communication c : repository.findByCaseId(tenantId, caseId)) {
            Instant at = c.getDirection() == CommunicationDirection.INBOUND && c.getReceivedAt() != null ? c.getReceivedAt()
                    : c.getSentAt() != null ? c.getSentAt() : c.getCreatedAt();
            String text = c.getSubject() != null && !c.getSubject().isBlank() ? c.getSubject() : snippet(c.getBody());
            out.add(new TimelineEntry("COMMUNICATION", at, c.getChannel().name(), c.getDirection().name(),
                    text + (c.getStatus() == CommunicationStatus.BLOCKED ? " [blocked: " + c.getStatusDetail() + "]" : ""),
                    c.getStatus().name(), c.getId()));
        }
        for (CollectionContactLog l : contactLogRepository.findByCaseId(tenantId, caseId)) {
            out.add(new TimelineEntry("CONTACT_LOG", l.getCreatedAt(), l.getContactMethod().name(), "OUTBOUND",
                    l.getOutcome() + (l.getNotes() != null && !l.getNotes().isBlank() ? " — " + snippet(l.getNotes()) : ""),
                    l.getOutcome().name(), l.getId()));
        }
        for (PtpEvent e : timelineRepository.findPtpEventsByCaseId(tenantId, caseId)) {
            out.add(new TimelineEntry("PTP", e.getCreatedAt(), null, null,
                    e.getEventType() + (e.getDetail() != null ? ": " + snippet(e.getDetail()) : ""), null, e.getPtpId()));
        }
        out.sort(Comparator.comparing(TimelineEntry::at).reversed());
        return out.size() > Math.max(1, Math.min(limit, 500)) ? out.subList(0, Math.max(1, Math.min(limit, 500))) : out;
    }

    public List<CommunicationPolicy.ChannelStatus> channelStatuses() {
        return java.util.Arrays.stream(CommunicationChannel.values()).map(policy::channelStatus).toList();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private SendResult transmit(TenantId tenantId, Communication queued, String to, Content content) {
        CollectionMessageSender sender = senders.orderedStream()
                .filter(s -> s.supportedChannels().contains(queued.getChannel())).findFirst().orElse(null);
        if (sender == null) {
            return SendResult.rejected("No sender configured for channel " + queued.getChannel());
        }
        try {
            return sender.send(new OutboundMessage(tenantId, queued.getId(), queued.getChannel(), to,
                    content.subject(), content.body()));
        } catch (Exception e) {
            log.warn("[Debt Collection] sender threw for communication {}: {}", queued.getId(), e.getMessage());
            return SendResult.rejected("Sender error: " + e.getMessage());
        }
    }

    private Content resolveContent(TenantId tenantId, DebtCollectionCase c, SendCommand cmd) {
        if (cmd.templateCode() != null && !cmd.templateCode().isBlank()) {
            CommunicationTemplate tpl = templateService.latest(tenantId, cmd.templateCode());
            if (tpl.getChannel() != cmd.channel()) {
                throw new IllegalArgumentException("Template " + tpl.getCode() + " is for " + tpl.getChannel()
                        + ", not " + cmd.channel());
            }
            Map<String, String> vars = new HashMap<>();
            vars.put("debtorName", c.getDebtorName());
            vars.put("caseNumber", c.getCaseNumber());
            vars.put("totalOutstanding", c.getTotalOutstanding().setScale(2, RoundingMode.HALF_UP).toPlainString());
            vars.put("openedDate", c.getOpenedDate().toString());
            if (cmd.variables() != null) {
                cmd.variables().forEach((k, v) -> { if (k.startsWith("custom.") && v != null) vars.put(k, v); });
            }
            return new Content(TemplateRenderer.render(tpl.getSubject(), vars), TemplateRenderer.render(tpl.getBody(), vars),
                    tpl.getCode(), tpl.getTemplateVersion());
        }
        if (cmd.body() == null || cmd.body().isBlank()) {
            throw new IllegalArgumentException("Provide a templateCode or a body");
        }
        if (cmd.channel() == CommunicationChannel.EMAIL && (cmd.subject() == null || cmd.subject().isBlank())) {
            throw new IllegalArgumentException("An email needs a subject");
        }
        return new Content(cmd.subject(), cmd.body(), null, null);
    }

    private String resolveRecipient(DebtCollectionCase c, SendCommand cmd) {
        if (cmd.recipient() != null && !cmd.recipient().isBlank()) return cmd.recipient().trim();
        String fallback = cmd.channel() == CommunicationChannel.EMAIL ? c.getDebtorEmail() : c.getDebtorPhone();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalArgumentException("No recipient: provide one, or add the debtor's "
                    + (cmd.channel() == CommunicationChannel.EMAIL ? "email" : "phone number") + " to the case");
        }
        return fallback.trim();
    }

    private SendOutcome outcome(Communication c) {
        Set<CommunicationChannel> alt = EnumSet.noneOf(CommunicationChannel.class);
        if (c.getStatus() == CommunicationStatus.BLOCKED) {
            alt.addAll(policy.enabledChannels());
            alt.remove(c.getChannel());
        }
        return new SendOutcome(c, alt);
    }

    /** Mirrors the contact into CRM's activity timeline, like CollectionContactLogService does. Content is not copied (data minimisation). */
    private void crmLog(TenantId tenantId, DebtCollectionCase c, Communication comm, UUID actorId) {
        if (c.getCustomerId() == null) return;
        String type = switch (comm.getChannel()) {
            case SMS -> "SMS";
            case EMAIL -> "EMAIL";
            case WHATSAPP -> "WHATSAPP";
            case LETTER -> "OTHER";
        };
        String text = "Debt collection " + comm.getDirection().name().toLowerCase() + " " + comm.getChannel() + " ("
                + c.getCaseNumber() + "): " + comm.getPurpose();
        bestEffort("CRM log", () -> crmFacade.logCommunication(tenantId, c.getCustomerId(), type, comm.getDirection().name(),
                text, Instant.now(), actorId));
    }

    /** Runs a side effect in its own transaction; a failure is logged and never undoes the primary record. */
    private void bestEffort(String what, Runnable r) {
        try {
            tx.executeWithoutResult(s -> r.run());
        } catch (Exception e) {
            log.warn("[Debt Collection] {} failed (ignored): {}", what, e.getMessage());
        }
    }

    private static String snippet(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 200 ? one.substring(0, 200) + "…" : one;
    }
}
