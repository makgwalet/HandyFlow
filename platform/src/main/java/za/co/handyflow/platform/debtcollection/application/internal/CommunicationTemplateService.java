package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationTemplate;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationTemplateRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Publishes versioned templates. Publishing an existing code creates the next version; old versions are kept forever. */
@Service
@RequiredArgsConstructor
public class CommunicationTemplateService {

    private final CommunicationTemplateRepository repository;

    @Transactional
    public CommunicationTemplate publish(TenantId tenantId, String code, CommunicationChannel channel, String subject,
                                         String body, UUID actorId) {
        String normalized = code == null ? null : code.trim().toUpperCase();
        if (!TemplateRenderer.isRenderable(body) || !TemplateRenderer.isRenderable(subject)) {
            throw new IllegalArgumentException("Unsupported placeholder. Allowed: " + TemplateRenderer.BUILT_IN
                    + " or any {{custom.name}} supplied at send time");
        }
        var latest = normalized == null ? java.util.Optional.<CommunicationTemplate>empty() : repository.findLatest(tenantId, normalized);
        if (latest.isPresent() && latest.get().getChannel() != channel) {
            throw new IllegalArgumentException("Template " + normalized + " already exists for channel "
                    + latest.get().getChannel() + " — use a different code for a different channel");
        }
        int next = normalized == null ? 1 : repository.maxVersion(tenantId, normalized) + 1;
        CommunicationTemplate t = CommunicationTemplate.publish(tenantId, normalized, next, channel, subject, body, actorId);
        latest.ifPresent(prev -> {
            prev.supersede();
            repository.saveAndFlush(prev); // clear the unique "one latest per code" slot before inserting the new row
        });
        return repository.save(t);
    }

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> listLatest(TenantId tenantId) {
        return repository.findAllLatest(tenantId);
    }

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> versions(TenantId tenantId, String code) {
        List<CommunicationTemplate> v = repository.findVersions(tenantId, code.trim().toUpperCase());
        if (v.isEmpty()) throw new ResourceNotFoundException("CommunicationTemplate", code);
        return v;
    }

    @Transactional(readOnly = true)
    public CommunicationTemplate latest(TenantId tenantId, String code) {
        return repository.findLatest(tenantId, code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("CommunicationTemplate", code));
    }
}
