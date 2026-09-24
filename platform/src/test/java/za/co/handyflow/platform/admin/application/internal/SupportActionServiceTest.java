package za.co.handyflow.platform.admin.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SupportAction;
import za.co.handyflow.platform.shared.SupportActionResult;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Regression test for SupportActionService — the dispatcher half of the
 * support-action framework. Uses hand-written SupportAction fakes rather
 * than mocks, since the interface's whole point is that real
 * implementations live in other modules; a fake here stands in for one
 * without this test needing to depend on identity at all — the same
 * decoupling the production design itself relies on.
 */
@ExtendWith(MockitoExtension.class)
class SupportActionServiceTest {

    @Mock private AdminAuditLogRepository auditRepo;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "support@handyflow.co.za";

    private static SupportAction fakeAction(String key, SupportActionResult result) {
        return new SupportAction() {
            @Override public String key() { return key; }
            @Override public String label() { return "Fake: " + key; }
            @Override public String description() { return "A fake action for testing"; }
            @Override public SupportActionResult execute(TenantId tenantId, UUID targetId, Map<String, String> params, UUID performedByAdminId) {
                return result;
            }
        };
    }

    private static SupportAction throwingAction(String key) {
        return new SupportAction() {
            @Override public String key() { return key; }
            @Override public String label() { return "Throwing: " + key; }
            @Override public String description() { return "Always throws"; }
            @Override public SupportActionResult execute(TenantId tenantId, UUID targetId, Map<String, String> params, UUID performedByAdminId) {
                throw new RuntimeException("boom");
            }
        };
    }

    @Test
    @DisplayName("listActions returns every registered action, sorted by label")
    void listActions_returnsAllRegistered() {
        var service = new SupportActionService(
                List.of(fakeAction("B_ACTION", SupportActionResult.success("ok")), fakeAction("A_ACTION", SupportActionResult.success("ok"))),
                auditRepo);

        var actions = service.listActions();

        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).label()).isEqualTo("Fake: A_ACTION"); // sorted, not insertion order
    }

    @Test
    @DisplayName("execute() throws for an unknown action key, without touching the audit log")
    void execute_unknownKey_throwsWithoutAuditing() {
        var service = new SupportActionService(List.of(), auditRepo);

        assertThatThrownBy(() -> service.execute("NONEXISTENT", TENANT_ID, null, null, ADMIN_ID, ADMIN_EMAIL, "127.0.0.1"))
                .isInstanceOf(HandyFlowException.class);

        verify(auditRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("a successful action is audited with success=true in the details")
    void execute_success_audited() {
        var service = new SupportActionService(List.of(fakeAction("TEST_ACTION", SupportActionResult.success("done"))), auditRepo);

        var result = service.execute("TEST_ACTION", TENANT_ID, null, null, ADMIN_ID, ADMIN_EMAIL, "127.0.0.1");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepo).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("SUPPORT_ACTION:TEST_ACTION");
        assertThat(captor.getValue().getDetails()).contains("\"success\":true");
    }

    @Test
    @DisplayName("an action that returns failure is still audited, with success=false")
    void execute_actionReturnsFailure_stillAudited() {
        var service = new SupportActionService(List.of(fakeAction("TEST_ACTION", SupportActionResult.failure("nope"))), auditRepo);

        var result = service.execute("TEST_ACTION", TENANT_ID, null, null, ADMIN_ID, ADMIN_EMAIL, "127.0.0.1");

        assertThat(result.success()).isFalse();
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepo).save(captor.capture());
        assertThat(captor.getValue().getDetails()).contains("\"success\":false");
    }

    @Test
    @DisplayName("an action that throws is caught, reported as a failure result, and still audited -- never propagated raw")
    void execute_actionThrows_caughtAndAudited() {
        var service = new SupportActionService(List.of(throwingAction("BROKEN_ACTION")), auditRepo);

        var result = service.execute("BROKEN_ACTION", TENANT_ID, null, null, ADMIN_ID, ADMIN_EMAIL, "127.0.0.1");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("boom");
        verify(auditRepo).save(any());
    }
}
