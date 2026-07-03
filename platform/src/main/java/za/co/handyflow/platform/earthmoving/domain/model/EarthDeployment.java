package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single deployment of an asset to a site/client, with a start and
 * (eventually) an end.
 * <p>
 * WHY this exists separately from {@code EarthAsset.currentSite} /
 * {@code currentClient}: those two fields only ever hold the LATEST value —
 * every redeployment silently overwrites whatever was there before, so
 * there was never any way to answer "where was this machine in April" or
 * "how many days did it spend on the SANRAL job total". This entity is the
 * append-only history; {@code EarthAsset.currentSite}/{@code currentClient}
 * remain as a cheap denormalized "where is it right now" for list views
 * that don't want to join against deployment history just to render a
 * fleet table.
 * <p>
 * A deployment is considered OPEN while {@code returnedAt} is null. It
 * closes not just when the asset explicitly returns to AVAILABLE, but
 * whenever it leaves DEPLOYED status for any reason — including breaking
 * down or being pulled into maintenance mid-job — see
 * {@code EarthAssetService#closeOpenDeploymentIfAny}. Without that, a
 * machine that broke down on-site would show as "still deployed" forever,
 * which is simply wrong.
 */
@Entity
@Table(name = "earthmoving_deployments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EarthDeployment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    /** Why the deployment ended: RETURNED, BREAKDOWN, or MAINTENANCE. Null while open. */
    @Column(name = "end_reason")
    private String endReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static EarthDeployment create(TenantId tenantId, UUID assetId, String siteName, String clientName,
                                         String contactName, String contactPhone,
                                         LocalDate plannedStartDate, LocalDate plannedEndDate, String notes) {
        EarthDeployment d = new EarthDeployment();
        d.tenantId = tenantId;
        d.assetId = assetId;
        d.siteName = siteName;
        d.clientName = clientName;
        d.contactName = contactName;
        d.contactPhone = contactPhone;
        d.plannedStartDate = plannedStartDate;
        d.plannedEndDate = plannedEndDate;
        d.notes = notes;
        d.deployedAt = Instant.now();
        d.createdAt = Instant.now();
        return d;
    }

    public boolean isOpen() {
        return returnedAt == null;
    }

    public void close(String endReason) {
        if (!isOpen()) {
            throw new IllegalStateException("Deployment " + id + " is already closed");
        }
        this.returnedAt = Instant.now();
        this.endReason = endReason;
    }
}
