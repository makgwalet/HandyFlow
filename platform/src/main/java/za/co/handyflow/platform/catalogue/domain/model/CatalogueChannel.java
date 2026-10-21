package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Somewhere items are sold or listed: the till, an online shop, a marketplace, a printed list. */
@Getter
@Entity
@Table(name = "catalogue_channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueChannel {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ChannelKind kind;

    /** The price list this channel sells from; empty means the default list. */
    @Column(name = "price_list_id")
    private UUID priceListId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static CatalogueChannel create(TenantId tenantId, String code, String name, ChannelKind kind, UUID priceListId) {
        var c = new CatalogueChannel();
        c.tenantId = tenantId;
        c.code = code;
        c.name = name;
        c.kind = kind;
        c.priceListId = priceListId;
        return c;
    }

    public void update(String name, ChannelKind kind, UUID priceListId, boolean active) {
        this.name = name;
        this.kind = kind;
        this.priceListId = priceListId;
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
