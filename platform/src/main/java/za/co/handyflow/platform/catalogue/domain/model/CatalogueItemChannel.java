package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** An item is published to a channel while this row exists. */
@Getter
@Entity
@Table(name = "catalogue_item_channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueItemChannel {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "channel_id", nullable = false, updatable = false)
    private UUID channelId;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt = Instant.now();

    @Column(name = "published_by", updatable = false)
    private UUID publishedBy;

    public static CatalogueItemChannel of(TenantId tenantId, UUID itemId, UUID channelId, UUID by) {
        var x = new CatalogueItemChannel();
        x.tenantId = tenantId;
        x.itemId = itemId;
        x.channelId = channelId;
        x.publishedBy = by;
        return x;
    }
}
