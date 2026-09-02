package za.co.handyflow.platform.trainingprovider.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import static org.junit.jupiter.api.Assertions.*;

class TrainProvClientTest {

    private TrainProvClient newClient() {
        return TrainProvClient.create(TenantId.generate(), "CLI-00001", "Acme Corp", "REG123", "Jane",
                "jane@acme.co.za", "0821234567", "1 Main St");
    }

    @Test
    void newClientStartsActive() {
        TrainProvClient client = newClient();
        assertEquals("ACTIVE", client.getStatus());
        assertFalse(client.isDeleted());
    }

    @Test
    void deactivateAndReactivateToggleStatus() {
        TrainProvClient client = newClient();
        client.deactivate();
        assertEquals("INACTIVE", client.getStatus());
        client.reactivate();
        assertEquals("ACTIVE", client.getStatus());
    }

    @Test
    void softDeleteSetsDeletedAt() {
        TrainProvClient client = newClient();
        client.softDelete();
        assertTrue(client.isDeleted());
    }
}
