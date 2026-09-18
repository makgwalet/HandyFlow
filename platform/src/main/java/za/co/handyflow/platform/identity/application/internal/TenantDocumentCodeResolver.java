package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.Tenant;
import za.co.handyflow.platform.identity.domain.repository.TenantRepository;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Separate bean (not a private method on {@link TenantNumberingEngine})
 * specifically so {@link Transactional} actually takes effect: Spring's
 * transactional behaviour is proxy-based, and a method calling another
 * method on {@code this} bypasses the proxy entirely, silently turning
 * {@code REQUIRES_NEW} into a no-op. Injecting this as a collaborator forces
 * the call through the proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class TenantDocumentCodeResolver {

    private final TenantRepository tenantRepository;

    /**
     * Returns this tenant's document_code, assigning one if it doesn't
     * already have one. Runs in its own transaction (REQUIRES_NEW), same
     * reasoning as {@code TenantSequenceService.nextValue} — a long batch
     * job numbering many documents for many tenants must not hold this
     * tenant's row lock across unrelated work.
     * <p>
     * Collision handling: V285's backfill derives a candidate for every
     * existing tenant from its name/slug without checking uniqueness (a
     * blocking migration must never fail on a data collision). This method
     * is where uniqueness is actually enforced going forward — for a tenant
     * whose backfilled or chosen code collides with another tenant's, a
     * numeric suffix is appended and persisted, so the code becomes unique
     * and stable from that point on. Already-collided historical codes
     * (two tenants both landing on the same backfilled code before either
     * issues a new document) are a known gap — see gap matrix — this
     * method only prevents new collisions going forward; it does not
     * retroactively rename a tenant's already-visible code.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String resolveDocumentCode(TenantId tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId.getValue())
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId.getValue()));

        if (tenant.getDocumentCode() != null) {
            return tenant.getDocumentCode();
        }

        String candidate = deriveCandidateCode(tenant.getName() != null ? tenant.getName() : tenant.getSlug());
        String resolved = candidate;
        int suffix = 1;
        while (tenantRepository.existsByDocumentCode(resolved)) {
            resolved = candidate + suffix;
            suffix++;
        }

        tenant.assignDocumentCode(resolved);
        tenantRepository.save(tenant);
        log.info("Assigned document code '{}' to tenant {}", resolved, tenantId.getValue());
        return resolved;
    }

    private String deriveCandidateCode(String source) {
        String alnum = source.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (alnum.isEmpty()) {
            alnum = "TEN";
        }
        return alnum.substring(0, Math.min(4, alnum.length()));
    }
}
