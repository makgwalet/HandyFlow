package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookDeliveryLog;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookEndpoint;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookPurpose;
import za.co.handyflow.platform.debtcollection.domain.repository.WebhookDeliveryLogRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.WebhookEndpointRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Admin side of webhook endpoints: create (token + secret shown ONCE), rotate the secret, revoke, inspect recent deliveries. */
@Service
@RequiredArgsConstructor
public class WebhookService {

    public record Created(WebhookEndpoint endpoint, String token, String secret) {}

    public record Rotated(WebhookEndpoint endpoint, String secret) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookEndpointRepository repository;
    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookProperties props;

    @Transactional
    public Created create(TenantId tenantId, String name, WebhookPurpose purpose, UUID actorId) {
        requireConfigured();
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        WebhookEndpoint e = repository.save(WebhookEndpoint.create(tenantId, name, purpose, WebhookSigner.sha256Hex(token), actorId));
        return new Created(e, token, WebhookSigner.deriveSecret(props.getSigningMasterKey(), e.getId(), e.getSecretVersion()));
    }

    @Transactional
    public Rotated rotateSecret(TenantId tenantId, UUID id) {
        requireConfigured();
        WebhookEndpoint e = find(tenantId, id);
        e.rotateSecret();
        repository.save(e);
        return new Rotated(e, WebhookSigner.deriveSecret(props.getSigningMasterKey(), e.getId(), e.getSecretVersion()));
    }

    @Transactional
    public WebhookEndpoint revoke(TenantId tenantId, UUID id) {
        WebhookEndpoint e = find(tenantId, id);
        e.revoke();
        return repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> list(TenantId tenantId) {
        return repository.findAllForTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryLog> recentLog(TenantId tenantId, UUID id, int limit) {
        find(tenantId, id);
        return logRepository.findRecent(tenantId, id, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new IllegalStateException("Webhooks are not configured: set handyflow.debtcollection.webhooks.signing-master-key "
                    + "(at least 32 characters) in the server configuration first");
        }
    }

    private WebhookEndpoint find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id.toString()));
    }
}
