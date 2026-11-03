package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.documents.application.RetentionFacade;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * FM records in the records retention engine. A work order or an asset is tracked once a file is attached to it. Its clock starts
 * when it is finished: a work order when it is completed (from its completion date) or cancelled or rejected (from that day), an
 * asset when it is decommissioned. The clock is started straight away when a file is added to a record that is already finished,
 * and otherwise by the nightly sync, so no work-order or asset transition has to know about retention.
 */
@Slf4j
@Service
public class FmRecordsService {

    public static final String MODULE = "FM";
    public static final String WORK_ORDER = "WORK_ORDER";
    public static final String ASSET = "ASSET";
    /** How the evidence module knows FM's files. */
    public static final String EVIDENCE_MODULE = "facilitiesmanagement";
    public static final String EVIDENCE_WORK_ORDER = "FmWorkOrder";
    public static final String EVIDENCE_ASSET = "FmAsset";
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final int SYNC_BATCH = 500;

    private final FmWorkOrderRepository workOrders;
    private final FmAssetRepository assets;
    private final FmClientRepository clients;
    private final FmSiteRepository sites;
    private final RetentionFacade retention;
    private final SchedulerLease lease;
    private final TransactionTemplate tx;

    FmRecordsService(FmWorkOrderRepository workOrders, FmAssetRepository assets, FmClientRepository clients, FmSiteRepository sites,
                     RetentionFacade retention, SchedulerLease lease, PlatformTransactionManager txManager) {
        this.workOrders = workOrders;
        this.assets = assets;
        this.clients = clients;
        this.sites = sites;
        this.retention = retention;
        this.lease = lease;
        this.tx = new TransactionTemplate(txManager);
    }

    /** A file was attached to the work order. Never fails the upload: a problem is logged and the nightly sync catches up. */
    public void fileAttachedToWorkOrder(TenantId tenantId, UUID workOrderId) {
        try {
            tx.executeWithoutResult(s -> workOrders.findActiveById(tenantId, workOrderId).ifPresent(w -> {
                trackWorkOrder(tenantId, w);
                if (w.isTerminal() && w.getRetentionClockAt() == null) startWorkOrderClock(tenantId, w);
            }));
        } catch (RuntimeException e) {
            log.warn("FM work order {}: not tracked for retention yet: {}", workOrderId, e.getMessage());
        }
    }

    public void fileAttachedToAsset(TenantId tenantId, UUID assetId) {
        try {
            tx.executeWithoutResult(s -> assets.findActiveById(tenantId, assetId).ifPresent(a -> {
                trackAsset(tenantId, a);
                if ("DECOMMISSIONED".equals(a.getStatus()) && a.getRetentionClockAt() == null) startAssetClock(tenantId, a);
            }));
        } catch (RuntimeException e) {
            log.warn("FM asset {}: not tracked for retention yet: {}", assetId, e.getMessage());
        }
    }

    void trackWorkOrder(TenantId tenantId, FmWorkOrder w) {
        String title = w.getDescription() == null || w.getDescription().isBlank() ? "Work order" : w.getDescription();
        retention.track(tenantId, MODULE, WORK_ORDER, w.getId(), WORK_ORDER, w.getWorkOrderNumber(), title, context(tenantId, w.getClientId(), w.getSiteId()));
    }

    void trackAsset(TenantId tenantId, FmAsset a) {
        UUID clientId = sites.findActiveById(tenantId, a.getSiteId()).map(s -> s.getClientId()).orElse(null);
        retention.track(tenantId, MODULE, ASSET, a.getId(), ASSET, a.getAssetTag(), a.getName(), context(tenantId, clientId, a.getSiteId()));
    }

    /** "Client · Site", or whatever of it still exists. */
    String context(TenantId tenantId, UUID clientId, UUID siteId) {
        String client = clientId == null ? null : clients.findActiveById(tenantId, clientId).map(c -> c.getTradingName()).orElse(null);
        String site = siteId == null ? null : sites.findActiveById(tenantId, siteId).map(s -> s.getName()).orElse(null);
        if (client == null) return site;
        return site == null ? client : client + " · " + site;
    }

    /** The day the work order finished: its completion date, otherwise (cancelled or rejected) its last change. */
    static LocalDate finishedOn(FmWorkOrder w) {
        Instant at = "COMPLETED".equals(w.getStatus()) && w.getCompletedAt() != null ? w.getCompletedAt()
                : w.getUpdatedAt() != null ? w.getUpdatedAt() : Instant.now();
        return at.atZone(ZONE).toLocalDate();
    }

    static LocalDate decommissionedOn(FmAsset a) {
        return (a.getUpdatedAt() != null ? a.getUpdatedAt() : Instant.now()).atZone(ZONE).toLocalDate();
    }

    private void startWorkOrderClock(TenantId tenantId, FmWorkOrder w) {
        retention.startClock(tenantId, MODULE, WORK_ORDER, List.of(w.getId()), finishedOn(w));
        w.markRetentionClockStarted();
        workOrders.save(w);
    }

    private void startAssetClock(TenantId tenantId, FmAsset a) {
        retention.startClock(tenantId, MODULE, ASSET, List.of(a.getId()), decommissionedOn(a));
        a.markRetentionClockStarted();
        assets.save(a);
    }

    /** Nightly: finished work orders and decommissioned assets get their clock started (untracked ones, with no files, are just marked). */
    @Scheduled(cron = "${app.facilitiesmanagement.records-sync.cron:0 25 3 * * *}", zone = "Africa/Johannesburg")
    void nightly() {
        try { lease.runExclusively("fm-records-sync", Duration.ofHours(1), this::syncAll); }
        catch (RuntimeException e) { log.error("FM records sync did not complete: {}", e.getMessage(), e); }
    }

    int syncAll() {
        int done = 0;
        for (FmWorkOrder w : workOrders.findFinishedWithoutRetentionClock(PageRequest.of(0, SYNC_BATCH))) {
            UUID id = w.getId(); TenantId t = w.getTenantId();
            try {
                tx.executeWithoutResult(s -> workOrders.findActiveById(t, id).ifPresent(x -> {
                    if (x.isTerminal() && x.getRetentionClockAt() == null) startWorkOrderClock(t, x);
                }));
                done++;
            } catch (RuntimeException e) { log.warn("FM work order {}: retention clock not started: {}", id, e.getMessage()); }
        }
        for (FmAsset a : assets.findDecommissionedWithoutRetentionClock(PageRequest.of(0, SYNC_BATCH))) {
            UUID id = a.getId(); TenantId t = a.getTenantId();
            try {
                tx.executeWithoutResult(s -> assets.findActiveById(t, id).ifPresent(x -> {
                    if ("DECOMMISSIONED".equals(x.getStatus()) && x.getRetentionClockAt() == null) startAssetClock(t, x);
                }));
                done++;
            } catch (RuntimeException e) { log.warn("FM asset {}: retention clock not started: {}", id, e.getMessage()); }
        }
        if (done > 0) log.info("FM records sync: {} record(s) brought up to date", done);
        return done;
    }
}
