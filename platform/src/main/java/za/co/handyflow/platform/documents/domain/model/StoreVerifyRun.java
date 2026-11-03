package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** One run of the document store check: how many files it checked, how many failed, and whether the storage round trip worked. */
@Entity
@Table(name = "document_store_verify_runs")
public class StoreVerifyRun {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String MANUAL = "MANUAL";

    @Id
    private UUID id;
    @Column(name = "trigger_type", nullable = false, updatable = false) private String triggerType;
    @Column(name = "triggered_by", updatable = false)                   private UUID triggeredBy;
    @Column(name = "started_at", nullable = false, updatable = false)   private Instant startedAt;
    @Column(name = "finished_at")                                       private Instant finishedAt;
    @Column(name = "checked", nullable = false)                         private int checked;
    @Column(name = "ok", nullable = false)                              private int ok;
    @Column(name = "failed", nullable = false)                          private int failed;
    @Column(name = "canary_ok")                                         private Boolean canaryOk;
    @Column(name = "canary_detail")                                     private String canaryDetail;

    protected StoreVerifyRun() {}

    public static StoreVerifyRun start(String triggerType, UUID triggeredBy) {
        StoreVerifyRun r = new StoreVerifyRun();
        r.id = UUID.randomUUID();
        r.triggerType = MANUAL.equals(triggerType) ? MANUAL : SCHEDULED;
        r.triggeredBy = triggeredBy;
        r.startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return r;
    }

    public void canary(boolean passed, String detail) {
        canaryOk = passed;
        canaryDetail = StoreRules.clip(detail, 300);
    }

    public void finish(int checked, int ok, int failed) {
        this.checked = checked;
        this.ok = ok;
        this.failed = failed;
        this.finishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getTriggerType() { return triggerType; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getChecked() { return checked; }
    public int getOk() { return ok; }
    public int getFailed() { return failed; }
    public Boolean getCanaryOk() { return canaryOk; }
    public String getCanaryDetail() { return canaryDetail; }
}
