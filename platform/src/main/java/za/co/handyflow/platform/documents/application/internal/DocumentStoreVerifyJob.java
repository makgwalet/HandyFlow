package za.co.handyflow.platform.documents.application.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly document store check: a write-read-delete round trip on the storage provider, then up to
 * {@code app.documents.store.verify.batch-size} files (those checked longest ago first) re-read and re-hashed, the encrypted
 * ones opened. A file that has just gone bad is logged as an error and written to the tenant's audit trail
 * (DOCUMENT_STORE_INTEGRITY_FAILED). Runs on one instance at a time (SchedulerLease). Off with
 * {@code app.documents.store.verify.enabled=false}.
 * <p>
 * Every file is checked once per (number of live files / batch size) nights. With the defaults (1,000 a night) a store of
 * 30,000 files is fully re-checked each month; raise the batch size if the store grows past that.
 */
@Component
@ConditionalOnProperty(name = "app.documents.store.verify.enabled", havingValue = "true", matchIfMissing = true)
class DocumentStoreVerifyJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentStoreVerifyJob.class);

    private final DocumentStoreImpl store;

    DocumentStoreVerifyJob(DocumentStoreImpl store) { this.store = store; }

    @Scheduled(cron = "${app.documents.store.verify.cron:0 40 2 * * *}", zone = "Africa/Johannesburg")
    void nightly() {
        try { store.runScheduled(); }
        catch (RuntimeException e) { log.error("[DocumentStore] The nightly check did not complete: {}", e.getMessage(), e); }
    }
}
