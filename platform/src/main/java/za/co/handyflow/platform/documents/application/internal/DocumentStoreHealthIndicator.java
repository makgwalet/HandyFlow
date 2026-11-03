package za.co.handyflow.platform.documents.application.internal;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.documents.application.DocumentStore;

/**
 * {@code /actuator/health} component "documentStore". DOWN only when the last finished check could not write, read back and
 * delete a file on the storage provider (uploads would fail too). Files that failed their check do NOT take the application
 * down (one damaged file must not pull an instance out of a load balancer): they show as {@code failingFiles} in the details,
 * and the full list is on {@code GET /api/v1/admin/document-store/health}. Counts only here, never keys or tenants.
 * Switch off with {@code management.health.documentStore.enabled=false}.
 */
@Component("documentStore")
@ConditionalOnEnabledHealthIndicator("documentStore")
class DocumentStoreHealthIndicator implements HealthIndicator {

    private final DocumentStoreImpl store;

    DocumentStoreHealthIndicator(DocumentStoreImpl store) { this.store = store; }

    @Override
    public Health health() {
        try {
            DocumentStore.Health h = store.health();
            DocumentStore.RunSummary last = store.lastFinishedRun();
            Health.Builder b = last != null && Boolean.FALSE.equals(last.canaryOk()) ? Health.down() : Health.up();
            b.withDetail("provider", h.provider())
             .withDetail("encryption", h.encryptionOn() ? "on" : "off")
             .withDetail("liveFiles", h.liveFiles())
             .withDetail("encryptedFiles", h.encryptedFiles())
             .withDetail("failingFiles", h.failingCount())
             .withDetail("neverChecked", h.byStatus().getOrDefault("UNVERIFIED", 0L));
            if (h.oldestCheckAt() != null) b.withDetail("oldestCheckAt", h.oldestCheckAt().toString());
            if (last != null) {
                b.withDetail("lastCheckAt", String.valueOf(last.finishedAt()));
                b.withDetail("lastCheckStorageRoundTrip", Boolean.TRUE.equals(last.canaryOk()) ? "ok" : String.valueOf(last.canaryDetail()));
            } else {
                b.withDetail("lastCheckAt", "never");
            }
            return b.build();
        } catch (RuntimeException e) {
            return Health.unknown().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
