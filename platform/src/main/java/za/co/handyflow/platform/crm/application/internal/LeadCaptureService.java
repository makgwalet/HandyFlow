package za.co.handyflow.platform.crm.application.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import za.co.handyflow.platform.crm.domain.model.CaptureKey;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.repository.CaptureKeyRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.CaptureKeyRequest;
import za.co.handyflow.platform.crm.dto.CaptureKeyResponse;
import za.co.handyflow.platform.crm.dto.CreateCustomerRequest;
import za.co.handyflow.platform.crm.dto.CreatedCaptureKeyResponse;
import za.co.handyflow.platform.crm.dto.LeadCaptureRequest;
import za.co.handyflow.platform.crm.dto.LeadCaptureResponse;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Lead capture keys, and taking in a lead sent with one. The public call has no login: the key names the account. Leads go through the
 * ordinary create path, so routing, workflows and the new-lead notification all apply. Capture is deliberately not one big transaction:
 * a "this looks like a duplicate" refusal from the create path must not poison anything else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCaptureService {
    private static final String KEY_PREFIX = "hf_crm_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CaptureKeyRepository keyRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final CrmAuditService audit;

    // ── keys ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CaptureKeyResponse> listKeys(TenantId tenantId) {
        return keyRepository.findAllForTenant(tenantId).stream().map(LeadCaptureService::toResponse).toList();
    }

    @Transactional
    public CreatedCaptureKeyResponse createKey(TenantId tenantId, UUID userId, CaptureKeyRequest req) {
        LeadCaptureRules.checkKeyName(req.name());
        if (keyRepository.findAllForTenant(tenantId).stream().filter(CaptureKey::isActive).count() >= LeadCaptureRules.MAX_KEYS) {
            throw new IllegalArgumentException("At most " + LeadCaptureRules.MAX_KEYS + " active keys. Revoke one you no longer use.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String apiKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var key = keyRepository.save(CaptureKey.create(tenantId, req.name(), hashKey(apiKey), apiKey.substring(0, 12), req.defaultSource() == null ? LeadSource.WEBSITE : req.defaultSource(), userId));
        audit.record(tenantId, AuditEntity.INTEGRATION, key.getId(), key.getName(), AuditAction.KEY_CREATED, "Lead capture key " + key.getKeyPrefix() + "…");
        return new CreatedCaptureKeyResponse(toResponse(key), apiKey);
    }

    @Transactional
    public CaptureKeyResponse revokeKey(TenantId tenantId, UUID id) {
        var key = keyRepository.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Capture key", id.toString()));
        if (key.isActive()) {
            key.revoke();
            keyRepository.save(key);
            audit.record(tenantId, AuditEntity.INTEGRATION, id, key.getName(), AuditAction.KEY_REVOKED, "Lead capture key " + key.getKeyPrefix() + "…");
        }
        return toResponse(key);
    }

    // ── the public call ─────────────────────────────────────────────────────

    public LeadCaptureResponse capture(String rawKey, LeadCaptureRequest req) {
        CaptureKey key = authenticate(rawKey);
        if (LeadCaptureRules.isSpam(req.website())) return new LeadCaptureResponse("IGNORED");
        var clean = LeadCaptureRules.clean(req.name(), req.company(), req.email(), req.phone(), req.message(), req.source(), key.getDefaultSource());
        TenantId tenantId = key.getTenantId();
        if (clean.email() != null && customerRepository.existsActiveByEmail(tenantId, clean.email())) return new LeadCaptureResponse("DUPLICATE");
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId.getValue().toString());
            customerService.createCustomer(tenantId, new CreateCustomerRequest(clean.recordName(), clean.email(), clean.phone(), null, null, clean.notes(),
                    CustomerType.LEAD, clean.source(), req.fields(), Set.of()));
            return new LeadCaptureResponse("CREATED");
        } catch (ConflictException e) {
            return new LeadCaptureResponse("DUPLICATE");   // an email or a very similar name is already on file
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.setTenantId(previous);
        }
    }

    private CaptureKey authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required");
        CaptureKey key = keyRepository.findByKeyHash(hashKey(rawKey.trim())).filter(CaptureKey::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required"));
        Instant now = Instant.now();
        key.touch(now);
        keyRepository.save(key);
        return key;
    }

    static String hashKey(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static CaptureKeyResponse toResponse(CaptureKey k) {
        return new CaptureKeyResponse(k.getId(), k.getName(), k.getKeyPrefix(), k.getDefaultSource(), k.getCreatedAt(), k.getLastUsedAt(), k.isActive());
    }
}
