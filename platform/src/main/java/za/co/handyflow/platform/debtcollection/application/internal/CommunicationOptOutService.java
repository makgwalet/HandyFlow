package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationOptOut;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationOptOutRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Do-not-contact register. Recording is idempotent per (contact, channel); revoking keeps the row for audit. */
@Service
@RequiredArgsConstructor
public class CommunicationOptOutService {

    /** Whole-message keywords that count as an opt-out on an inbound SMS/WhatsApp. "CANCEL"/"END" are excluded: too ambiguous in a payment conversation. */
    private static final Set<String> KEYWORDS = Set.of("STOP", "STOPALL", "UNSUBSCRIBE", "QUIT", "OPTOUT", "OPT OUT");

    private final CommunicationOptOutRepository repository;

    static boolean isOptOutKeyword(String body) {
        return body != null && KEYWORDS.contains(body.trim().toUpperCase().replaceAll("[.!]+$", ""));
    }

    @Transactional
    public CommunicationOptOut record(TenantId tenantId, String contact, CommunicationChannel channel, String reason,
                                      String source, UUID actorId, String actorName) {
        String normalized = ContactNormalizer.normalize(contact);
        var existing = repository.findActiveExact(tenantId, normalized, channel);
        if (!existing.isEmpty()) return existing.get(0);
        return repository.save(CommunicationOptOut.of(tenantId, normalized, channel, reason, source, actorId, actorName));
    }

    @Transactional
    public CommunicationOptOut revoke(TenantId tenantId, UUID id, String reason, UUID actorId) {
        CommunicationOptOut o = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("CommunicationOptOut", id.toString()));
        o.revoke(reason, actorId);
        return repository.save(o);
    }

    @Transactional(readOnly = true)
    public boolean isOptedOut(TenantId tenantId, String normalizedContact, CommunicationChannel channel) {
        return !repository.findActiveCovering(tenantId, normalizedContact, channel).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<CommunicationOptOut> listActive(TenantId tenantId) {
        return repository.findAllActive(tenantId);
    }
}
