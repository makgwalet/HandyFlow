package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.AuditEventRepository;
import za.co.handyflow.platform.security.domain.repository.GuardDocumentRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.GuardTokenRepository;
import za.co.handyflow.platform.security.dto.UpdateGuardStatusRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Not run in the authoring sandbox (no Maven jars). */
class GuardStatusTokenRevocationTest {

    private final GuardRepository guards = mock(GuardRepository.class);
    private final GuardTokenRepository tokens = mock(GuardTokenRepository.class);
    private final GuardService service = new GuardService(guards, mock(GuardDocumentRepository.class),
            mock(JdbcTemplate.class), mock(AuditEventRepository.class), tokens);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID id = UUID.randomUUID();

    private void guardExists() {
        Guard g = mock(Guard.class);
        when(guards.findActiveById(tenant, id)).thenReturn(Optional.of(g));
    }

    @Test void suspendRevokesTokens() {
        guardExists();
        service.updateStatus(tenant, id, new UpdateGuardStatusRequest("SUSPENDED", "no show"), UUID.randomUUID());
        verify(tokens).revokeAllForGuard(eq(id), any(Instant.class), contains("SUSPENDED"));
    }

    @Test void terminateRevokesTokens() {
        guardExists();
        service.updateStatus(tenant, id, new UpdateGuardStatusRequest("TERMINATED", "dismissed"), UUID.randomUUID());
        verify(tokens).revokeAllForGuard(eq(id), any(Instant.class), contains("TERMINATED"));
    }

    @Test void backToActiveKeepsTokens() {
        guardExists();
        service.updateStatus(tenant, id, new UpdateGuardStatusRequest("ACTIVE", null), UUID.randomUUID());
        verifyNoInteractions(tokens);
    }

    @Test void deleteRevokesTokens() {
        guardExists();
        service.deleteGuard(tenant, id, UUID.randomUUID());
        verify(tokens).revokeAllForGuard(eq(id), any(Instant.class), anyString());
    }
}
