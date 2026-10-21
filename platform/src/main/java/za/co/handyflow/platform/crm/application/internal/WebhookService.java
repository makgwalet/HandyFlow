package za.co.handyflow.platform.crm.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.Webhook;
import za.co.handyflow.platform.crm.domain.model.CrmWebhookDelivery;
import za.co.handyflow.platform.crm.domain.model.WebhookEvent;
import za.co.handyflow.platform.crm.domain.repository.CrmWebhookDeliveryRepository;
import za.co.handyflow.platform.crm.domain.repository.WebhookRepository;
import za.co.handyflow.platform.crm.dto.CreatedWebhookResponse;
import za.co.handyflow.platform.crm.dto.WebhookDeliveryResponse;
import za.co.handyflow.platform.crm.dto.WebhookRequest;
import za.co.handyflow.platform.crm.dto.WebhookResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Outgoing webhooks. publish() is called next to the change it reports; it waits for the change to commit (so a rolled-back change tells
 * nobody), hands the message to a small background pool, and never throws. The receiver has 5 seconds, redirects are not followed, and
 * addresses that point inside a network are refused both when saved and again when sent. After 10 failures in a row a webhook is suspended.
 */
@Slf4j
@Service("crmWebhookService")
public class WebhookService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebhookRepository webhookRepository;
    private final CrmWebhookDeliveryRepository deliveryRepository;
    private final CrmAuditService audit;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500), r -> {
        Thread t = new Thread(r, "crm-webhooks");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.AbortPolicy());

    public WebhookService(WebhookRepository webhookRepository, CrmWebhookDeliveryRepository deliveryRepository, CrmAuditService audit, ObjectMapper mapper, PlatformTransactionManager txManager) {
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.audit = audit;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    @PreDestroy
    void stop() { pool.shutdown(); }

    // ── admin ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WebhookResponse> list(TenantId tenantId) {
        return webhookRepository.findAllActive(tenantId).stream().map(WebhookService::toResponse).toList();
    }

    @Transactional
    public CreatedWebhookResponse create(TenantId tenantId, WebhookRequest req) {
        WebhookRules.check(req.name(), req.url(), req.events());
        if (webhookRepository.findAllActive(tenantId).size() >= WebhookRules.MAX_WEBHOOKS) throw new IllegalArgumentException("At most " + WebhookRules.MAX_WEBHOOKS + " webhooks");
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var w = webhookRepository.save(Webhook.create(tenantId, req.name(), req.url(), secret, WebhookRules.joinEvents(req.events()), req.active() == null || req.active()));
        audit.record(tenantId, AuditEntity.INTEGRATION, w.getId(), w.getName(), AuditAction.CREATED, "Webhook to " + hostOf(w.getUrl()));
        return new CreatedWebhookResponse(toResponse(w), secret);
    }

    @Transactional
    public WebhookResponse update(TenantId tenantId, UUID id, WebhookRequest req) {
        var w = require(tenantId, id);
        WebhookRules.check(req.name(), req.url(), req.events());
        w.change(req.name(), req.url(), WebhookRules.joinEvents(req.events()), req.active() == null || req.active());
        audit.record(tenantId, AuditEntity.INTEGRATION, id, w.getName(), AuditAction.UPDATED, w.isActive() ? "Webhook to " + hostOf(w.getUrl()) : "Turned off");
        return toResponse(webhookRepository.save(w));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        var w = require(tenantId, id);
        w.softDelete();
        webhookRepository.save(w);
        audit.record(tenantId, AuditEntity.INTEGRATION, id, w.getName(), AuditAction.DELETED, null);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> recentDeliveries(TenantId tenantId, int limit) {
        Map<UUID, String> names = new LinkedHashMap<>();
        webhookRepository.findAllActive(tenantId).forEach(w -> names.put(w.getId(), w.getName()));
        return deliveryRepository.findRecent(tenantId, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))).stream()
                .map(d -> new WebhookDeliveryResponse(d.getId(), d.getWebhookId(), names.getOrDefault(d.getWebhookId(), "Deleted webhook"), d.getEvent(), d.getOutcome(), d.getStatusCode(), d.getDetail(), d.getSentAt())).toList();
    }

    /** Sends a ping now and answers with how it went, so a person can check the address and the signature before relying on it. */
    public WebhookDeliveryResponse test(TenantId tenantId, UUID id) {
        Webhook w = tx.execute(s -> require(tenantId, id));
        String body = body(WebhookEvent.PING, Map.of("message", "This is a test from HandyFlow."));
        CrmWebhookDelivery d = send(w, WebhookEvent.PING, body);
        record(w.getId(), tenantId, d, true);
        return new WebhookDeliveryResponse(d.getId(), w.getId(), w.getName(), d.getEvent(), d.getOutcome(), d.getStatusCode(), d.getDetail(), d.getSentAt());
    }

    // ── publishing ──────────────────────────────────────────────────────────

    /** Tells every active webhook that listens for this event. Never throws. */
    public void publish(TenantId tenantId, WebhookEvent event, Customer lead) {
        try {
            List<Webhook> hooks = webhookRepository.findAllActive(tenantId).stream()
                    .filter(w -> w.isActive() && !w.isSuspended() && WebhookRules.parseEvents(w.getEvents()).contains(event)).toList();
            if (hooks.isEmpty()) return;
            String body = body(event, customerData(lead));
            Runnable send = () -> hooks.forEach(w -> submit(tenantId, w, event, body));
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { send.run(); }
                });
            } else {
                send.run();
            }
        } catch (Exception e) {
            log.warn("[CRM] Webhooks for {} on lead={} tenant={} could not be queued: {}", event, lead.getId(), tenantId, e.getMessage());
        }
    }

    private void submit(TenantId tenantId, Webhook w, WebhookEvent event, String body) {
        try {
            pool.execute(() -> {
                try {
                    CrmWebhookDelivery d = send(w, event, body);
                    record(w.getId(), tenantId, d, false);
                } catch (Exception e) {
                    log.warn("[CRM] Webhook {} delivery could not be recorded tenant={}: {}", w.getId(), tenantId, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[CRM] Webhook queue is full; {} for webhook {} dropped tenant={}", event, w.getId(), tenantId);
        }
    }

    /** Makes the call. Never throws: every problem becomes a failed delivery with a readable detail. */
    private CrmWebhookDelivery send(Webhook w, WebhookEvent event, String body) {
        try {
            WebhookRules.checkUrl(w.getUrl());
            URI uri = URI.create(w.getUrl());
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (WebhookRules.isBlocked(a)) return CrmWebhookDelivery.of(w.getTenantId(), w.getId(), event, false, null, "The address points inside a private network, so it was not called");
            }
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header("User-Agent", "HandyFlow-Webhooks/1")
                    .header("X-HandyFlow-Event", event.name()).header("X-HandyFlow-Delivery", UUID.randomUUID().toString())
                    .header("X-HandyFlow-Signature", WebhookRules.sign(w.getSecret(), body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return CrmWebhookDelivery.of(w.getTenantId(), w.getId(), event, code >= 200 && code < 300, code, WebhookRules.statusText(code));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CrmWebhookDelivery.of(w.getTenantId(), w.getId(), event, false, null, "Interrupted");
        } catch (java.net.http.HttpTimeoutException e) {
            return CrmWebhookDelivery.of(w.getTenantId(), w.getId(), event, false, null, "The receiver did not answer within 5 seconds");
        } catch (Exception e) {
            return CrmWebhookDelivery.of(w.getTenantId(), w.getId(), event, false, null, "Could not reach the address: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Saves the attempt, updates the webhook's failure count (a test does not count against it) and trims old log lines. */
    private void record(UUID webhookId, TenantId tenantId, CrmWebhookDelivery d, boolean isTest) {
        tx.executeWithoutResult(s -> {
            deliveryRepository.save(d);
            deliveryRepository.deleteOlderThan(webhookId, Instant.now().minus(Duration.ofDays(30)));
            webhookRepository.findActiveById(tenantId, webhookId).ifPresent(w -> {
                boolean wasSuspended = w.isSuspended();
                if (!isTest) w.recordResult("DONE".equals(d.getOutcome()));
                webhookRepository.save(w);
                if (!wasSuspended && w.isSuspended()) audit.record(tenantId, AuditEntity.INTEGRATION, w.getId(), w.getName(), AuditAction.STOPPED, "Suspended after " + Webhook.SUSPEND_AFTER + " failures in a row");
            });
        });
    }

    private String body(WebhookEvent event, Map<String, Object> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event", event.name());
        m.put("occurredAt", Instant.now().toString());
        m.put("data", data);
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the message", e);
        }
    }

    private static Map<String, Object> customerData(Customer c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", c.getId());
        d.put("name", c.getName());
        d.put("email", c.getEmail());
        d.put("phone", c.getPhone());
        d.put("type", c.getCustomerType() == null ? null : c.getCustomerType().name());
        d.put("stage", c.getPipelineStage() == null ? null : c.getPipelineStage().name());
        d.put("source", c.getLeadSource() == null ? null : c.getLeadSource().name());
        d.put("dealValue", c.getDealValue());
        d.put("ownerId", c.getOwnerId());
        return d;
    }

    private Webhook require(TenantId tenantId, UUID id) {
        return webhookRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Webhook", id.toString()));
    }

    private static String hostOf(String url) {
        try { return URI.create(url.strip()).getHost(); } catch (Exception e) { return "the address"; }
    }

    static WebhookResponse toResponse(Webhook w) {
        return new WebhookResponse(w.getId(), w.getName(), w.getUrl(), WebhookRules.parseEvents(w.getEvents()), w.isActive(), w.isSuspended(), w.getConsecutiveFailures(), w.getLastDeliveryAt());
    }
}
