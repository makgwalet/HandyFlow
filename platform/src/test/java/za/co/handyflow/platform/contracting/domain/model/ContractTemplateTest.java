package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractTemplateTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    @Test
    void editRaisesVersionAndSanitisesBody() {
        ContractTemplate t = ContractTemplate.create(tenant, "NDA", "NDA", "d", "<p>a</p>", Map.of(), false);
        assertEquals(1, t.getTemplateVersion());
        t.edit("NDA v2", "NDA", "d2", "<p>b</p><script>alert(1)</script>", Map.of());
        assertEquals(2, t.getTemplateVersion());
        assertEquals("<p>b</p>", t.getBodyTemplate());
        assertEquals("NDA v2", t.getName());
    }

    @Test
    void systemTemplatesCannotBeEditedOrArchived() {
        ContractTemplate t = ContractTemplate.create(tenant, "NDA", "NDA", "d", "<p>a</p>", Map.of(), true);
        assertThrows(IllegalStateException.class, () -> t.edit("x", "NDA", "d", "<p>b</p>", Map.of()));
        assertThrows(IllegalStateException.class, t::archive);
    }

    @Test
    void archiveHidesCustomTemplate() {
        ContractTemplate t = ContractTemplate.create(tenant, "NDA", "NDA", "d", "<p>a</p>", Map.of(), false);
        t.archive();
        assertFalse(t.isActive());
    }
}
