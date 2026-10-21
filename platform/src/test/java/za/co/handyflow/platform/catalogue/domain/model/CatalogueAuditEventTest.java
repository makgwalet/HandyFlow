package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.CatalogueItemChanged;
import za.co.handyflow.platform.shared.TenantId;

class CatalogueAuditEventTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    @Test
    void anItemLineAnnouncesTheChangeWithoutAnyPrice() {
        var id = UUID.randomUUID();
        var events = CatalogueAuditEvent.of(TENANT, "ITEM", id, "Cement", CatalogueAuditAction.PRICE_CHANGED, "R 80 to R 90", null).domainEvents();
        assertThat(events.size()).isEqualTo(1);
        var e = (CatalogueItemChanged) events.iterator().next();
        assertThat(e.itemId()).isEqualTo(id);
        assertThat(e.action()).isEqualTo("PRICE_CHANGED");
        assertThat(e.tenantId()).isEqualTo(TENANT);
        assertThat(e.toString()).doesNotContain("R 80");
    }

    @Test
    void linesAboutOtherThingsAnnounceNothing() {
        assertThat(CatalogueAuditEvent.of(TENANT, "CHANNEL", UUID.randomUUID(), "Web", CatalogueAuditAction.CHANNEL_CHANGED, null, null).domainEvents().isEmpty()).isTrue();
        assertThat(CatalogueAuditEvent.of(TENANT, "SETTING", UUID.randomUUID(), "x", CatalogueAuditAction.SETTING_CHANGED, null, null).domainEvents().isEmpty()).isTrue();
    }
}
